package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;

@Mapper(componentModel = "spring")
public interface BankMapper {
    BankDto toDto(Bank bank);
}
