package com.ftk.tpip.release.domain.service;
import com.ftk.tpip.release.domain.model.CompiledNotificationTemplate;
public interface NotificationTemplateEngine {
    CompiledNotificationTemplate compile(String templateDocument,String variableSchema);
    String render(String templateDocument,String variableSchema,String contextDocument);
}
