package vg.template.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import vg.template.BaseRestControllerTest;
import vg.template.model.TemplateModel;
import vg.template.rest.v1.TemplateServiceApiRestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

class TemplateControllerTest extends BaseRestControllerTest {

    @Autowired
    TemplateServiceApiRestClient restClient;

    @Autowired
    ApplicationContext applicationContext;

    private String name;
    private String description;

    private Instant creationTime;

    @BeforeEach
    void setUp() {
        name = nextString();
        description = nextString();
        creationTime = clock.instant();
    }

    @Test
    void create() {
        var savedModel = restClient.create(buildModel());

        assertThat(
                savedModel.getName()
        ).isEqualTo(
                name
        );

        assertThat(
                savedModel.getDescription()
        ).isEqualTo(
                description
        );

        assertThat(
                savedModel.getCreatedAtTime()
        ).isEqualTo(
                creationTime
        );

        assertThat(
                savedModel.getUniqueId()
        ).isNotNull();


        assertThat(
                savedModel.getVersion()
        ).isEqualTo(
                0
        );

        assertThat(restClient.getAll()).contains(savedModel);
    }

    @Test
    void update() {
        var savedModel = restClient.create(buildModel());

        var savedModelId = savedModel.getUniqueId();
        var newName = nextString();
        var newDescription = nextString();
        var newCreationTime = creationTime.plusSeconds(10 + nextLong());

        savedModel.setName(newName);
        savedModel.setDescription(newDescription);
        savedModel.setCreatedAtTime(newCreationTime);

        var updatedModel = restClient.update(savedModel);

        assertThat(
                updatedModel.getName()
        ).isEqualTo(
                newName
        );

        assertThat(
                updatedModel.getDescription()
        ).isEqualTo(
                newDescription
        );

        assertThat(
                updatedModel.getCreatedAtTime()
        ).isEqualTo(
                newCreationTime
        );

        assertThat(
                updatedModel.getUniqueId()
        ).isEqualTo(savedModelId);

        assertThat(
                updatedModel.getVersion()
        ).isEqualTo(
                1
        );

        assertThat(restClient.getAll()).contains(updatedModel);
    }

    private TemplateModel buildModel() {
        return TemplateModel.builder()
                .uniqueId(null)
                .name(name)
                .description(description)
                .createdAtTime(creationTime)
                .build();
    }

}