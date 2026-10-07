package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusalCode;
import za.co.titandynamix.sasbo_online_backend.mapper.SubscriptionsMapper;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.services.SubscriptionsService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionsServiceImpl implements SubscriptionsService {

    private final SubscriptionsRepository subscriptionRepository;
    private final SubscriptionsMapper subscriptionsMapper;
    private final BankRepository bankRepository;

    @Override
    public List<SubscriptionsDto> findAll(Long bankId) {
        List<Subscriptions> subs = bankId == null ? this.subscriptionRepository.findAllWithBank()
                : this.subscriptionRepository.findByBankIdWithBank(bankId);
        return subs.stream()
                .map(this.subscriptionsMapper::toDto)
                .toList();
    }

    @Override
    public Optional<SubscriptionsDto> findById(Long id) {
        return this.subscriptionRepository.findByIdWithBank(id)
                .map(this.subscriptionsMapper::toDto);
    }

    /**
     * A new row for a bank. ⚠️ A name that matches no bank is still saved with none -- register row A5, pinned by
     * {@code anUnmatchableBankNameIsAnError}, and not this change's to settle.
     *
     * <p>A bank that already has its row is refused (A23's route, once the browser reached it): every reader takes a
     * bank's row as THE row -- capture takes the first, and a debit order run refuses a bank with two
     * ({@code BANK_SUBSCRIPTION_AMBIGUOUS}) -- so a second one would stop the bank's next run. A transaction, so the
     * check and the write are one unit.
     */
    @Override
    @Transactional
    public SubscriptionsDto create(SubscriptionsDto dto) {
        Subscriptions subscription = this.subscriptionsMapper.toEntity(dto);
        if (dto.bankName() != null) {
            String q = dto.bankName().toLowerCase();
            this.bankRepository.findByNameIgnoreCase(dto.bankName())
                .or(() -> this.bankRepository.findAll().stream()
                        .filter(b -> b.getName().toLowerCase().contains(q) || q.contains(b.getName().toLowerCase()))
                        .findFirst())
                .ifPresent(bank -> {
                    refuseASecondRow(bank);
                    subscription.setBank(bank);
                });
        }
        Subscriptions saved = this.subscriptionRepository.save(subscription);
        return this.subscriptionsMapper.toDto(saved);
    }

    private void refuseASecondRow(Bank bank) {
        if (bank.getId() != null && this.subscriptionRepository.existsByBank_Id(bank.getId())) {
            throw new ReferenceRefusal(ReferenceRefusalCode.SUBSCRIPTION_BANK_TAKEN,
                    bank.getName() + " already has its subscription row. Change that row instead of adding another.",
                    Map.of("bankName", bank.getName()));
        }
    }

    /**
     * 🔴 Register row <b>A33</b>: this answered <b>500 after committing the write</b>.
     *
     * <p>Two things were wrong and both are fixed here. It loaded the row with
     * {@code findById}, leaving {@code bank} a lazy proxy, and it carried no
     * transaction — so {@code save} committed in its own unit of work and the
     * mapping below then ran with <b>no session</b> to initialise that proxy. The
     * caller was told the save had failed while the money table had already
     * changed, which is worse than either failure alone: the screen kept showing
     * the old value, and the obvious response was to try again.
     *
     * <p>{@code @Transactional} makes the read, the write and <b>the mapping</b>
     * one unit, which is the contract; {@code findByIdWithBank} — the fetch join
     * already sitting in the repository for exactly this — means the mapping does
     * not depend on lazy loading at all. Either alone would fix the symptom; both
     * together mean the DTO is built from data that is provably present.
     *
     * <p>⚠️ {@code create} above has the same shape and does <b>not</b> fail today,
     * because the bank it sets is an entity it just loaded rather than a proxy.
     * That is luck rather than design, and it is left for {@code 00.7}'s refactor
     * pass rather than changed here on no failing test.
     */
    @Override
    @Transactional
    public Optional<SubscriptionsDto> update(Long id, SubscriptionsDto dto) {
        return this.subscriptionRepository.findByIdWithBank(id)
                .map(existing -> {
                    this.subscriptionsMapper.updateFromDto(dto, existing);
                    if (dto.bankName() != null) {
                        String q = dto.bankName().toLowerCase();
                        this.bankRepository.findByNameIgnoreCase(dto.bankName())
                            .or(() -> this.bankRepository.findAll().stream()
                                    .filter(b -> b.getName().toLowerCase().contains(q) || q.contains(b.getName().toLowerCase()))
                                    .findFirst())
                            .ifPresent(existing::setBank);
                    } else {
                        existing.setBank(null);
                    }
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
