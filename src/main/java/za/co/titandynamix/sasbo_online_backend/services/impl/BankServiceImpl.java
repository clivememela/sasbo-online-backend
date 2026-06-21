package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankServiceImpl implements BankService {

    private final BankRepository bankRepository;

    @Override
    public List<Bank> findAll() {
        // Use JPA Sort to order results at the database level (case-insensitive by name)
        return this.bankRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Override
    public Optional<Bank> findByNameIgnoreCase(String bankName) {
        return this.bankRepository.findByNameIgnoreCase(bankName);
    }

    @Override
    public Bank save(Bank bank) {
        return this.bankRepository.save(bank);
    }
}
