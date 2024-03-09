package vg.template.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vg.template.mapper.TemplateMapper;
import vg.template.model.TemplateModel;
import vg.template.repository.TemplateRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.Collection;

@Slf4j
@RequiredArgsConstructor
@Service
public class TemplateServiceImpl implements TemplateService {

    private final UniqueIdService uniqueIdService;
    private final TemplateRepository repository;
    private final TemplateMapper mapper;

    @Override
    //TODO check permissions
    public TemplateModel create(TemplateModel event) {
        var entity = repository.saveWithNewUniqueId(
            mapper.toEntity(event),
            uniqueIdService
        );

        return mapper.toModel(entity);
    }

    @Override
    //TODO check permissions
    public TemplateModel update(TemplateModel event) {
        var entity = repository.findById(event.getUniqueId()).orElse(null);

        if (null == entity) {
            log.error("Entity was not found by id: {}", event.getUniqueId());
            throw new EntityNotFoundException();
        }

        mapper.updateEntity(entity, event);
        mapper.updateEvent(event, repository.save(entity));

        return event;
    }

    @Override
    public Collection<TemplateModel> getAll() {
        return repository.findAll().stream()
                .map(mapper::toModel)
                .toList();
    }
}
