package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vg.identity.mapper.UserMapper;
import vg.identity.model.User;
import vg.identity.repository.UserRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.Collection;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final UniqueIdService uniqueIdService;
    private final UserRepository repository;
    private final UserMapper mapper;

    @Override
    //TODO check permissions
    public User create(User user) {
        var entity = repository.saveWithNewUniqueId(
            mapper.toEntity(user),
            uniqueIdService
        );

        return mapper.toModel(entity);
    }

    @Override
    //TODO check permissions
    public User update(User user) {
        var entity = repository.findById(user.getUniqueId()).orElse(null);

        if (null == entity) {
            log.error("Entity was not found by id: {}", user.getUniqueId());
            throw new EntityNotFoundException();
        }

        mapper.updateEntity(entity, user);
        mapper.updateModel(user, repository.save(entity));

        return user;
    }

    @Override
    public Collection<User> getAll() {
        return repository.findAll().stream()
                .map(mapper::toModel)
                .toList();
    }
}
