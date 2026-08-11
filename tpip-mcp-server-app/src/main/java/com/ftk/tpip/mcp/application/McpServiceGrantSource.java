package com.ftk.tpip.mcp.application;

import com.ftk.tpip.mcp.model.McpClientIdentity;
import java.util.Set;

public interface McpServiceGrantSource {

    Set<String> grantedServiceCodes(McpClientIdentity identity);
}
