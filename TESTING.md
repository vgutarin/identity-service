# Testing Notes

## Unit Tests

- Use `@ExtendWith(MockitoExtension.class)` for Mockito-based tests.
- Use `@Mock` fields instead of manual `mock(...)` setup.
- Create the service under test in `@BeforeEach` after mocks are initialized.
- Mock collaborators such as `ObjectMapper`; do not use real infrastructure dependencies in focused unit tests.
- Stub serializer/deserializer calls explicitly when testing service behavior around payloads.
- Do not verify methods that were explicitly stubbed, such as `repository.save(...)`.
- Capture data passed to stubbed calls inside the stub answer when assertions need to inspect it.
- Verify only meaningful side effects or calls that are not already covered by stubbing.

Example pattern:

```java
@ExtendWith(MockitoExtension.class)
class SomeServiceTest {
    @Mock
    private SomeRepository repository;
    @Mock
    private ObjectMapper objectMapper;

    private SomeService service;

    @BeforeEach
    void setUp() {
        service = new SomeService(repository, objectMapper);
    }

    @Test
    void method_whenInputProvided_savesExpectedEntity() throws Exception {
        var savedEntity = new AtomicReference<SomeEntity>();
        when(repository.save(any(SomeEntity.class))).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, SomeEntity.class);
            savedEntity.set(entity);
            return entity;
        });

        service.method(input);

        assertThat(savedEntity.get().getStatus()).isEqualTo(ExpectedStatus.VALUE);
    }
}
```
