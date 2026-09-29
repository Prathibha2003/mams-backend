package com.mams.repository;

import com.mams.model.InitialBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InitialBalanceRepository extends JpaRepository<InitialBalance, InitialBalance.Key> {
}