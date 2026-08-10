package com.ftk.tpip.access.domain.repository;

import com.ftk.tpip.access.domain.model.ChannelAuthenticationVersion;
import java.util.List;
import java.util.Optional;

public interface ChannelAuthenticationRepository {
    Optional<ChannelAuthenticationVersion> findVersionById(long channelId, long versionId);
    List<ChannelAuthenticationVersion> findVersions(long channelId);
    ChannelAuthenticationVersion createVersion(ChannelAuthenticationVersion version, String actor);
    ChannelAuthenticationVersion publishVersion(long channelId, long versionId, String actor);
}
