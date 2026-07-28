package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;

import java.util.List;
import java.util.Optional;

public interface SubscriptionsService {
    
    List<SubscriptionsDto> findAll(Long bankId);
    
    Optional<SubscriptionsDto> findById(Long id);
    
    SubscriptionsDto create(SubscriptionsDto dto);
    
    Optional<SubscriptionsDto> update(Long id, SubscriptionsDto dto);
    
    boolean delete(Long id);
}
