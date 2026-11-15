# Testing Notes

## Persisted enum contracts

Follow the enum persistence and append-only compatibility rules in [Java conventions](AGENTS.md#java-conventions).
Every enum used in an entity requires both contract and persistence coverage:

- **Contract tests:** Pin literal names and ordinal numbers for every enum constant, and assert the complete
  order of values. Tests must fail if a constant is renamed, removed, reordered, or inserted between existing
  constants. When extending an enum, preserve all existing expected name/ordinal pairs and append the new
  expectations; do not regenerate expectations from the enum under test.
- **Persistence integration tests:** Exercise each entity enum mapping against the real test database.
  Verify that raw stored values are numeric ordinals and that loading the entity restores the expected enum
  constants. Cover every constant and null where the mapped field is nullable; keep schema nullability intact.
- Add or update both levels of coverage in the same change as a new enum, appended value, or entity enum
  mapping change. Both must pass before the change is complete.

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
