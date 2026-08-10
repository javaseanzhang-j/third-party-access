package com.ftk.tpip.control.application.access;

import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.access.domain.repository.CredentialProfileRepository;
import com.ftk.tpip.provider.domain.model.CredentialStatus;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.repository.CredentialRefRepository;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialProfileApplicationService {
    private final CredentialProfileRepository profiles;
    private final ProviderRepository providers;
    private final CredentialRefRepository secretRefs;
    public CredentialProfileApplicationService(CredentialProfileRepository profiles, ProviderRepository providers,
            CredentialRefRepository secretRefs) {
        this.profiles = profiles; this.providers = providers; this.secretRefs = secretRefs;
    }

    @Transactional(readOnly = true)
    public List<CredentialProfileView> list(Long providerId) {
        return profiles.findAll(providerId).stream().map(value -> view(value, profiles.findItems(value.id()))).toList();
    }

    @Transactional
    public CredentialProfileView create(long providerId, String code, String name, String type,
            String description, List<ItemInput> items, String actor) {
        var provider = providers.findById(providerId).orElseThrow(() -> new IllegalArgumentException("providerId does not exist"));
        if (provider.status() != ProviderStatus.ACTIVE) throw new IllegalArgumentException("provider must be ACTIVE");
        AssetCode normalized = AssetCode.of(code);
        if (profiles.findByCode(providerId, normalized.value()).isPresent())
            throw new IllegalArgumentException("credential profile code already exists under the provider");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("credential profile requires at least one field");
        var codes = new HashSet<String>();
        for (ItemInput item : items) {
            if (!codes.add(AssetCode.of(item.fieldCode()).value())) throw new IllegalArgumentException("credential field code must be unique");
            validateSecret(providerId, item);
        }
        CredentialProfile created = profiles.create(CredentialProfile.create(providerId, normalized, name, type, description), actor(actor));
        for (ItemInput item : items) {
            AssetCode fieldCode = AssetCode.of(item.fieldCode());
            CredentialProfileItem value = item.valueSource() == CredentialValueSource.PUBLIC_VALUE
                    ? CredentialProfileItem.publicValue(created.id(), fieldCode, item.fieldName(), item.publicValue(), item.sensitive(), item.description())
                    : CredentialProfileItem.secretRef(created.id(), fieldCode, item.fieldName(), item.secretRefId(), item.description());
            profiles.addItem(value, actor(actor));
        }
        return view(created, profiles.findItems(created.id()));
    }

    private void validateSecret(long providerId, ItemInput item) {
        if (item.valueSource() != CredentialValueSource.SECRET_REF) return;
        if (item.secretRefId() == null) throw new IllegalArgumentException("SECRET_REF field requires secretRefId");
        var ref = secretRefs.findById(item.secretRefId()).orElseThrow(() -> new IllegalArgumentException("secretRefId does not exist"));
        if (ref.providerId() != providerId || ref.status() != CredentialStatus.ACTIVE)
            throw new IllegalArgumentException("Secret reference must be ACTIVE and belong to the profile provider");
    }
    private static CredentialProfileView view(CredentialProfile value, List<CredentialProfileItem> items) {
        return new CredentialProfileView(value, items.stream().map(item -> new ItemView(item.id(), item.fieldCode().value(),
                item.fieldName(), item.valueSource(), item.sensitive() ? "******" : item.publicValue(), item.secretRefId(),
                item.sensitive(), item.description())).toList());
    }
    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) throw new IllegalArgumentException("X-Operator is invalid");
        return value.trim();
    }
    public record ItemInput(String fieldCode, String fieldName, CredentialValueSource valueSource,
            String publicValue, Long secretRefId, boolean sensitive, String description) {}
    public record ItemView(long id, String fieldCode, String fieldName, CredentialValueSource valueSource,
            String displayValue, Long secretRefId, boolean sensitive, String description) {}
    public record CredentialProfileView(CredentialProfile profile, List<ItemView> items) {}
}
