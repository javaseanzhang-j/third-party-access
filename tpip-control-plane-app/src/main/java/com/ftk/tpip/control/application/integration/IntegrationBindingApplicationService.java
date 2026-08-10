package com.ftk.tpip.control.application.integration;

import com.ftk.tpip.catalog.domain.model.OperationStatus;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.integration.domain.exception.BindingCodeAlreadyExistsException;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingRepository;
import com.ftk.tpip.provider.domain.model.ContractStatus;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.shared.AssetCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationBindingApplicationService {
    private final IntegrationBindingRepository repository;private final CanonicalOperationRepository operations;private final ProviderContractRepository contracts;
    public IntegrationBindingApplicationService(IntegrationBindingRepository repository,CanonicalOperationRepository operations,ProviderContractRepository contracts){this.repository=repository;this.operations=operations;this.contracts=contracts;}
    @Transactional public IntegrationBinding create(String code,String name,long operationId,long providerContractId,String owner,String actor){
        requireActiveReferences(operationId,providerContractId);AssetCode assetCode=AssetCode.of(code);String user=actor(actor);
        if(repository.findByCode(assetCode).isPresent())throw new BindingCodeAlreadyExistsException(assetCode.value());
        return repository.create(IntegrationBinding.create(assetCode,name,operationId,providerContractId,owner),user);
    }
    @Transactional(readOnly=true) public IntegrationBinding get(long id){return repository.findById(id).orElseThrow(()->new IntegrationBindingNotFoundException(id));}
    @Transactional(readOnly=true) public IntegrationBindingPage findAll(Long operationId,Long providerContractId,String keyword,BindingStatus status,int page,int size){
        if(page<0||size<1||size>200)throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 200");long offset=(long)page*size;if(offset>Integer.MAX_VALUE)throw new IllegalArgumentException("page offset is too large");var q=new IntegrationBindingQuery(operationId,providerContractId,keyword,status,(int)offset,size);return new IntegrationBindingPage(repository.findAll(q),page,size,repository.count(q));
    }
    @Transactional public IntegrationBinding update(long id,String name,String owner,BindingStatus status,long version,String actor){var current=get(id);if(status==BindingStatus.ACTIVE)requireActiveReferences(current.operationId(),current.providerContractId());return repository.update(current.revise(name,owner,status,version),actor(actor));}
    private void requireActiveReferences(long operationId,long contractId){var op=operations.findById(operationId).orElseThrow(()->new BindingReferenceException("operationId does not exist: "+operationId));if(op.status()!=OperationStatus.ACTIVE)throw new BindingReferenceException("operationId must reference an ACTIVE canonical operation");var contract=contracts.findById(contractId).orElseThrow(()->new BindingReferenceException("providerContractId does not exist: "+contractId));if(contract.status()!=ContractStatus.ACTIVE)throw new BindingReferenceException("providerContractId must reference an ACTIVE provider contract");}
    private static String actor(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("X-Operator must not be blank");String n=value.trim();if(n.length()>100)throw new IllegalArgumentException("X-Operator must not exceed 100 characters");return n;}
}
