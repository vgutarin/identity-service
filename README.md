# identity-service

### Definitions

- `IdentityUser` - user of the system (Person, or Organization, or Software)
- `IdentityUserInfo` - information about the user. Can be changed by the user.
- `IdentityUserSystemRole` - role of the system. Is used inside identity service to determine what user can do
  - `Admin` - has access to all system resources
  - `User` - has access to his own resources
  - `Client` - client of the system. Can get access to some part of `IdentityUserInfo` after `IdentityUser` gave him an access

- `IdentityUserClientManagedRole` - roles can be created by the client and assigned to the user (per cleint).
  - `Guest`
  - `User`  
  - `ChatManager` ...


## TODO

- introduce IdentityClient ROLE (chat bot, site, service, etc)
- implement logic for client to be authenticated and authorized (actually it will be a user with corresponded role)
- implement Logic to allow IdentityUser give/revoke access to his personal info per client
- ??? implement logic for client to store some data per user

- rework User to User { uniqueId, details } 
- use email as unique username