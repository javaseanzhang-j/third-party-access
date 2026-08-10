package com.ftk.tpip.control.application.access;

import com.ftk.tpip.access.domain.model.AccessChannelStatus;
import com.ftk.tpip.access.domain.model.ProviderProduct;
import com.ftk.tpip.access.domain.repository.ProviderProductRepository;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderProductApplicationService {
    private final ProviderProductRepository products;
    private final ProviderRepository providers;
    public ProviderProductApplicationService(ProviderProductRepository products, ProviderRepository providers) {
        this.products = products; this.providers = providers;
    }

    @Transactional(readOnly = true)
    public List<ProviderProduct> list(Long providerId) { return products.findAll(providerId); }

    @Transactional(readOnly = true)
    public List<Long> interfaceIds(long productId) {
        products.findById(productId).orElseThrow(() -> new IllegalArgumentException("providerProductId does not exist"));
        return products.findInterfaceIds(productId);
    }

    @Transactional
    public ProviderProduct create(long providerId, String code, String name, String description, String actor) {
        var provider = providers.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("providerId does not exist"));
        if (provider.status() != ProviderStatus.ACTIVE) throw new IllegalArgumentException("provider must be ACTIVE");
        AssetCode normalized = AssetCode.of(code);
        if (products.findByCode(providerId, normalized.value()).isPresent())
            throw new IllegalArgumentException("productCode already exists under the provider");
        return products.create(ProviderProduct.create(providerId, normalized, name, description), actor(actor));
    }

    @Transactional(readOnly = true)
    public ProviderProduct requireActive(long id, long providerId) {
        ProviderProduct product = products.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("providerProductId does not exist"));
        if (product.providerId() != providerId || product.status() != AccessChannelStatus.ACTIVE)
            throw new IllegalArgumentException("product must be ACTIVE and belong to the channel provider");
        return product;
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100)
            throw new IllegalArgumentException("X-Operator must contain 1 to 100 characters");
        return value.trim();
    }
}
