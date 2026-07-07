package vg.identity.model.user.channel;

import vg.identity.model.IdentityUserChannel;


public class IdentityUserChannelEmail extends IdentityUserChannel {
    public String getEmail() {
        return getChannelUserId();
    }
}
