package vg.identity.repository;


import vg.identity.entity.UserEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

public interface UserRepository extends UniqueIdJpaRepository<UserEntity> {
}
