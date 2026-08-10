package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;

/** Executes only the access-parameter IR frozen in a Bundle endpoint snapshot. */
public final class RuntimeAccessParameterAssembler {
    private static final java.util.regex.Pattern HEADER=java.util.regex.Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}$");
    private final ObjectMapper json;private final SecretResolver secrets;private final Clock clock;private final Supplier<UUID> uuids;
    public RuntimeAccessParameterAssembler(ObjectMapper json,SecretResolver secrets,Clock clock){this(json,secrets,clock,UUID::randomUUID);}
    RuntimeAccessParameterAssembler(ObjectMapper json,SecretResolver secrets,Clock clock,Supplier<UUID> uuids){this.json=Objects.requireNonNull(json);this.secrets=Objects.requireNonNull(secrets);this.clock=Objects.requireNonNull(clock);this.uuids=Objects.requireNonNull(uuids);}
    public JsonNode assemble(JsonNode endpoint,InvocationContext context){JsonNode plan=endpoint.path("accessParameterPlan");if(!plan.isObject())return endpoint;
        ObjectNode prepared=endpoint.deepCopy();String path=prepared.path("resourcePath").asText();List<String> query=new ArrayList<>();List<String> cookies=new ArrayList<>();
        for(JsonNode parameter:plan.path("parameters")){String code=text(parameter,"code"),location=text(parameter,"location");JsonNode value=resolve(parameter,context);
            if((value==null||value.isNull()||value.isMissingNode())&&parameter.path("required").asBoolean())throw new IllegalArgumentException("Required access parameter is missing: "+code);
            if(value==null||value.isNull()||value.isMissingNode())continue;String scalar=value.isValueNode()?value.asText():value.toString();
            if(scalar.indexOf('\r')>=0||scalar.indexOf('\n')>=0)throw new IllegalArgumentException("Access parameter contains forbidden line break: "+code);
            switch(location){case "HEADER"->{if(!HEADER.matcher(code).matches())throw new IllegalArgumentException("Invalid access parameter header: "+code);if(parameter.path("sensitive").asBoolean())context.putSensitiveTransportHeader(code,scalar);else context.putTransportHeader(code,scalar);}case "COOKIE"->cookies.add(code+"="+encode(scalar));case "QUERY"->query.add(encode(code)+"="+encode(scalar));case "PATH"->{String token="{"+code+"}";if(!path.contains(token)&&parameter.path("required").asBoolean())throw new IllegalArgumentException("Endpoint path is missing parameter token: "+token);path=path.replace(token,encode(scalar));}case "BODY"->{if(!context.providerRequest().isObject())throw new IllegalArgumentException("BODY access parameter requires object provider request");ObjectNode body=context.providerRequest().deepCopy();body.set(code,value);context.providerRequest(body);}case "SIGNATURE"->{String key="signature_"+code.replaceAll("[^A-Za-z0-9_-]","_");if(parameter.path("sensitive").asBoolean())context.putSensitiveAttribute(key,scalar);else context.putAttribute(key,scalar);}default->throw new IllegalArgumentException("Unsupported access parameter location: "+location);}
        }
        if(!cookies.isEmpty())context.putSensitiveTransportHeader("Cookie",String.join("; ",cookies));if(!query.isEmpty())path+=(path.contains("?")?"&":"?")+String.join("&",query);prepared.put("resourcePath",path);return prepared;}
    private JsonNode resolve(JsonNode p,InvocationContext c){return switch(text(p,"source")){case "FIXED"->p.get("value");case "SECRET_REF"->secret(text(p,"secretReference"));case "REQUEST"->select(c.canonicalRequest(),text(p,"sourceSelector"));case "MAPPING_OUTPUT"->select(c.providerRequest(),text(p,"sourceSelector"));case "POLICY_OUTPUT"->{Object v=c.attributes().get(text(p,"sourceSelector"));yield v==null?NullNode.instance:json.valueToTree(v);}case "SYSTEM_TIME"->TextNode.valueOf(clock.instant().toString());case "UUID"->TextNode.valueOf(uuids.get().toString());default->throw new IllegalArgumentException("Unsupported compiled access parameter source: "+p.path("source").asText());};}
    private JsonNode secret(String reference){if(!secrets.supports(reference))throw new IllegalStateException("No Secret Resolver supports access parameter reference");try(SecretValue secret=secrets.resolve(reference)){char[] chars=secret.copy();try{return TextNode.valueOf(new String(chars));}finally{Arrays.fill(chars,'\0');}}}
    private static JsonNode select(JsonNode root,String selector){if(root==null)return NullNode.instance;String path=selector.startsWith("$.")?selector.substring(2):selector;JsonNode current=root;for(String part:path.split("\\.")){if(part.isBlank())continue;current=current.path(part);}return current;}
    private static String text(JsonNode node,String field){String value=node.path(field).asText();if(value.isBlank())throw new IllegalArgumentException("Compiled access parameter is missing "+field);return value;}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
}
