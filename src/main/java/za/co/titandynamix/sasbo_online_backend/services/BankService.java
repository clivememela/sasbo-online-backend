package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;

import java.util.List;
import java.util.Optional;

public interface BankService {
    List<Bank> findAll();

    Optional<Bank> findByNameIgnoreCase(String bankName);

    Bank save(Bank bank);
}
