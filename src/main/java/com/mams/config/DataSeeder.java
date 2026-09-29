package com.mams.config;

import com.mams.model.*;
import com.mams.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final BaseRepository baseRepo;
    private final EquipmentTypeRepository typeRepo;
    private final InitialBalanceRepository balanceRepo;
    private final PasswordEncoder encoder;

    public DataSeeder(UserRepository users, BaseRepository baseRepo, EquipmentTypeRepository typeRepo,
                      InitialBalanceRepository balanceRepo, PasswordEncoder encoder) {
        this.users = users;
        this.baseRepo = baseRepo;
        this.typeRepo = typeRepo;
        this.balanceRepo = balanceRepo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        List<Base> bases = baseRepo.findAll();
        List<EquipmentType> types = typeRepo.findAll();

        if (balanceRepo.count() == 0) {
            for (Base b : bases) {
                for (EquipmentType t : types) {
                    InitialBalance ib = new InitialBalance();
                    ib.setBaseId(b.getId());
                    ib.setEquipmentTypeId(t.getId());
                    ib.setQuantity(100);
                    balanceRepo.save(ib);
                }
            }
        }

        if (users.count() == 0 && !bases.isEmpty()) {
            Base alpha = bases.stream()
                    .filter(b -> "Alpha Base".equals(b.getName()))
                    .findFirst().orElse(bases.get(0));
            users.save(make("admin", "admin123", "System Administrator", Role.ADMIN, null));
            users.save(make("commander", "commander123", "Alpha Base Commander", Role.BASE_COMMANDER, alpha));
            users.save(make("logistics", "logistics123", "Alpha Logistics Officer", Role.LOGISTICS_OFFICER, alpha));
        }
    }

    private User make(String username, String rawPassword, String fullName, Role role, Base base) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setFullName(fullName);
        u.setRole(role);
        u.setBase(base);
        return u;
    }
}