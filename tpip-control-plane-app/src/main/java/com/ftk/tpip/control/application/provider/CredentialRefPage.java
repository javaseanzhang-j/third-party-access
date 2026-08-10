package com.ftk.tpip.control.application.provider;

import com.ftk.tpip.provider.domain.model.CredentialRef;
import java.util.List;

public record CredentialRefPage(List<CredentialRef> items, int page, int size, long totalElements) {}
