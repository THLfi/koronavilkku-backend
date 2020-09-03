# How to contribute

We welcome contributions from the public. However there are some guidelines to follow:

1. Prior to committing to work, **create an issue** describing a problem, bug or enchancement you would to like to work on
2. **Wait for discussion** on the issue and how it should be solved
3. **Wait for main contributors** of the repository to handle the issue and clear it for implementation
4. Embrace the feedback and be patient. We are working as fast as we can to improve Koronavilkku and commnunity's help is much appreciated

## Issues

The issue tracker is the preferred channel for reports and queries.

## Pull requests

Our team will review the pull requests and contributors will get credit. You can see all contributors in AUTHORS.md.

## Commit message styleguide

We follow [The seven rules of a great Git commit message](https://chris.beams.io/posts/git-commit/).

### TL;DR

- Title line max 50 characters
- Single empty line between title and detailed description when description is needed
- Explain what and why vs. how
- Use the imperative mood in the title line
  - :heavy_check_mark: Refactor subsystem X for readability
  - :heavy_check_mark: Update getting started documentation
  - :heavy_check_mark: Remove deprecated methods
  - :heavy_check_mark: Release version 1.0.0
  - :x: Fixed bug with Y
  - :x: Changing behavior of X
  - :x: More fixes for broken stuff
- Attach issue tracker references at bottom after single empty line `Resolves: #123` or `See also: #456, #789`

## Code Style
Like all matters of taste, code style comes down to individual preferences. 
Within a single project however, it's best to stick with a uniform style.
If you need more detail, refer to "how it's done elsewhere in the codebase" as the no.1 guide.

### Java
- **You should** prefer self-documenting code to comments
  - When the code is not evident enough though, you should comment
- **Never** use nulls
  - Use Optional instead to mark values that may be missing
  - Nulls from outside APIs should be wrapped in Optional "at the border"
- **You should** strive to "make illegal states unrepresentable"
  - Validate inputs in constructor and throw an exception if the data is not good
- **Always** implement request fails as exceptions
  - These are handled in a single place in ApiErrorHandler
- **Always** pass SQL parameters as named params; don't concatenate them into the SQL String
- **Always** read ResultSet contents with column names, not indices
- **You shouldn't** move JDBC parameter-map keys into constants, as that just messes with IDE code assistance and moves it farther from the use-place
- **You should** favor pure functions and immutable structures
- **Always** use constructor injection and final fields for Spring DI
- **You should** use Java 8 date/time API for times
  - GAEN API also mixes UTC interval numbers and using them as-is is also fine

### SQL
- **Always** write SQL directly, no ORMs
- **Never** use the default schema
- **Always** use schema-name as qualifier in table names
- **Always** name tables and columns with snake_case
- **Never** uppercase keywords
  - Rely on your IDE to highlight SQL code, just like any other language
- **You may** write short SQLs in String variables so that they reside where they're used
- **You may** write longer SQLs in separate SQL files and load them as cached resources
- **Always** write migrations in separate .sql files, using Flyway notation for naming
- **You should** favor timestamptz for timestamps
  - GAEN API also mixes UTC interval numbers and using them as-is is also fine
