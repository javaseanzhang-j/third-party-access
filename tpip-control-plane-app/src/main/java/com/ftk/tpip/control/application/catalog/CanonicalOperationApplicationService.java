package com.ftk.tpip.control.application.catalog;

import com.ftk.tpip.catalog.domain.exception.OperationCodeAlreadyExistsException;
import com.ftk.tpip.catalog.domain.model.*;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.shared.AssetCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanonicalOperationApplicationService {
    private final CanonicalOperationRepository repository;
    public CanonicalOperationApplicationService(CanonicalOperationRepository repository){this.repository=repository;}
    @Transactional public CanonicalOperation create(long capabilityId,String code,String name,String description,
            InvocationMode mode,IdempotencyClass idempotency,DataClassification classification,String owner,String actor){
        requireActiveCapability(capabilityId); AssetCode assetCode=AssetCode.of(code); String user=actor(actor);
        if(repository.findByCode(assetCode).isPresent())throw new OperationCodeAlreadyExistsException(assetCode.value());
        return repository.create(CanonicalOperation.create(capabilityId,assetCode,name,description,mode,idempotency,classification,owner),user);
    }
    @Transactional(readOnly=true) public CanonicalOperation get(long id){return repository.findById(id).orElseThrow(()->new CanonicalOperationNotFoundException(id));}
    @Transactional(readOnly=true) public CanonicalOperationPage findAll(Long capabilityId,String keyword,OperationStatus status,int page,int size){
        if(page<0||size<1||size>200)throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 200");
        long offset=(long)page*size;if(offset>Integer.MAX_VALUE)throw new IllegalArgumentException("page offset is too large");
        var q=new CanonicalOperationQuery(capabilityId,keyword,status,(int)offset,size);return new CanonicalOperationPage(repository.findAll(q),page,size,repository.count(q));
    }
    @Transactional public CanonicalOperation update(long id,String name,String description,InvocationMode mode,
            IdempotencyClass idempotency,DataClassification classification,String owner,OperationStatus status,long version,String actor){
        var current=get(id);requireActiveCapability(current.capabilityId());return repository.update(current.revise(name,description,mode,idempotency,classification,owner,status,version),actor(actor));
    }
    private void requireActiveCapability(long id){if(!repository.capabilityIsActive(id))throw new IllegalArgumentException("capabilityId must reference an ACTIVE capability");}
    private static String actor(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("X-Operator must not be blank");String n=value.trim();if(n.length()>100)throw new IllegalArgumentException("X-Operator must not exceed 100 characters");return n;}
}
