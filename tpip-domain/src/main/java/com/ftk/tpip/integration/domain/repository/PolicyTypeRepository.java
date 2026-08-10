package com.ftk.tpip.integration.domain.repository;
import com.ftk.tpip.integration.domain.model.*;import java.util.*;
public interface PolicyTypeRepository{Optional<PolicyTypeDefinition> findById(long id);Optional<PolicyTypeDefinition> findByReference(String code,String semanticVersion);List<PolicyTypeDefinition> findAll(Boolean activeOnly);PolicyTypeDefinition create(PolicyTypeDefinition definition,String actor);PolicyTypeDefinition setStatus(long id,PolicyTypeStatus status,String actor);}
