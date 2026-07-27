package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.mapper.SubscriptionsMapper;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.services.SubscriptionsService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionsServiceImpl implements SubscriptionsService {

    private final SubscriptionsRepository subscriptionRepository;
    private final SubscriptionsMapper subscriptionsMapper;

    @Override
    public List<SubscriptionsDto> findAll() {
        return this.subscriptionRepository.findAll()
                .stream()
                .map(this.subscriptionsMapper::toDto)
                .toList();
    }

    @Override
    public Optional<SubscriptionsDto> findById(Long id) {
        return this.subscriptionRepository.findById(id)
                .map(this.subscriptionsMapper::toDto);
    }

    @Override
    public SubscriptionsDto create(SubscriptionsDto dto) {
        Subscriptions subscription = this.subscriptionsMapper.toEntity(dto);
        Subscriptions saved = this.subscriptionRepository.save(subscription);
        return this.subscriptionsMapper.toDto(saved);
    }

    @Override
    public Optional<SubscriptionsDto> update(Long id, SubscriptionsDto dto) {
        return this.subscriptionRepository.findById(id)
                .map(existing -> {
                    this.subscriptionsMapper.updateFromDto(dto, existing);
                    Subscriptions updated = this.subscriptionRepository.save(existing);
                    return this.subscriptionsMapper.toDto(updated);
                });
    }

    @Override
    public boolean delete(Long id) {
        if (!this.subscriptionRepository.existsById(id)) {
            return false;
        }
        this.subscriptionRepository.deleteById(id);
        return true;
    }
}
