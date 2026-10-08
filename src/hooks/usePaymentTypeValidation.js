import { useMemo } from 'react';

/**
 * Custom hook for Payment Type validation logic
 * Determines which banking fields should be visible based on payment type
 */
export const usePaymentTypeValidation = (paymentType) => {
  return useMemo(() => {
    const isBankAccountRequired = paymentType === 'Debit Order';
    const isPayrollDeduction = paymentType === 'Payroll Deduction';

    return {
      isBankAccountRequired,
      isPayrollDeduction,
      shouldShowBankAccountField: isBankAccountRequired,
      shouldShowDebitDayField: isBankAccountRequired,
      bankAccountFieldRequired: isBankAccountRequired,
      bankAccountFieldDisabled: isPayrollDeduction,
      debitDayFieldRequired: isBankAccountRequired,
      debitDayFieldDisabled: isPayrollDeduction,
    };
  }, [paymentType]);
};

export default usePaymentTypeValidation;
