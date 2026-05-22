package vg.identity.rest.v1;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import vg.identity.model.IdentityUser;
import vg.identity.service.IdentityUserService;

@HttpExchange("user")
public interface IdentityUserServiceApiRestClient extends IdentityUserService {

    @Override
    @PostExchange("create")
    IdentityUser create(@RequestBody IdentityUser user);

    @Override
    @PutExchange("update")
    IdentityUser update(@RequestBody IdentityUser user);
}
