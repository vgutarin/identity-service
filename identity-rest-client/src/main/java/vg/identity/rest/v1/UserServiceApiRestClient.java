package vg.identity.rest.v1;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import vg.identity.model.User;
import vg.identity.service.UserService;

import java.util.Collection;

@HttpExchange("user")
public interface UserServiceApiRestClient extends UserService {

    @Override
    @PostExchange("create")
    User create(@RequestBody User user);

    @Override
    @PutExchange("update")
    User update(@RequestBody User user);

    @Override
    @GetExchange("all")
    Collection<User> getAll();
}
