package edu.citu.opc.fams.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Lets other modules show who recorded something, without touching the user repository. */
@Service
public class UserDirectory {

    private final AppUserRepository users;

    public UserDirectory(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> namesByIds(Collection<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        if (ids.isEmpty()) return m;
        users.findAllById(ids).forEach(u -> m.put(u.getId(), u.getFullName()));
        return m;
    }
}
