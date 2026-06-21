package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;

import java.util.List;

/**
 * Java interface for bank branch operations.
 * A Kotlin interface with the same name also exists in the codebase; this Java interface
 * ensures Java compilation units can reference the service type.
 */
public interface BankBranchService {
    List<BankBranch> findAll();

    BankBranch save(BankBranch branch);
}

