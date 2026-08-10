package com.ftk.tpip.control.api.access;

import com.ftk.tpip.access.domain.model.ProviderProduct;
import com.ftk.tpip.control.application.access.ProviderProductApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/product-model/provider-products")
public class ProviderProductController {
    private final ProviderProductApplicationService service;
    public ProviderProductController(ProviderProductApplicationService service) { this.service = service; }

    @GetMapping public List<Response> list(@RequestParam(required = false) Long providerId) {
        return service.list(providerId).stream().map(Response::from).toList();
    }
    @PostMapping public ResponseEntity<Response> create(@Valid @RequestBody Create request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        ProviderProduct created = service.create(request.providerId(), request.productCode(), request.productName(),
                request.description(), actor);
        return ResponseEntity.created(URI.create("/control/v1/product-model/provider-products/" + created.id()))
                .body(Response.from(created));
    }
    @GetMapping("/{productId}/interfaces") public List<Long> interfaces(@PathVariable @Min(1) long productId) {
        return service.interfaceIds(productId);
    }
    public record Create(@Min(1) long providerId, @NotBlank @Size(max=180) String productCode,
            @NotBlank @Size(max=200) String productName, @Size(max=1000) String description) {}
    public record Response(long id, long providerId, String productCode, String productName,
            String description, String status, Instant createdAt) {
        static Response from(ProviderProduct value) { return new Response(value.id(), value.providerId(),
                value.productCode().value(), value.productName(), value.description(), value.status().name(), value.createdAt()); }
    }
}
