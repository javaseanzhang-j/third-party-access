package com.ftk.tpip.adapters.persistence.jdbc.access;

import com.ftk.tpip.access.domain.model.AccessChannelStatus;
import com.ftk.tpip.access.domain.model.ProviderProduct;
import com.ftk.tpip.access.domain.repository.ProviderProductRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcProviderProductRepository implements ProviderProductRepository {
    private static final String COLUMNS = "id,provider_id,product_code,product_name,description,status,created_at";
    private static final RowMapper<ProviderProduct> MAPPER = (rs, rowNum) -> new ProviderProduct(rs.getLong("id"),
            rs.getLong("provider_id"), AssetCode.of(rs.getString("product_code")), rs.getString("product_name"),
            rs.getString("description"), AccessChannelStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;
    public JdbcProviderProductRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<ProviderProduct> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_provider_product WHERE id=?", MAPPER, id).stream().findFirst();
    }
    @Override public Optional<ProviderProduct> findByCode(long providerId, String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_provider_product WHERE provider_id=? AND product_code=?",
                MAPPER, providerId, code).stream().findFirst();
    }
    @Override public List<ProviderProduct> findAll(Long providerId) {
        return providerId == null
                ? jdbc.query("SELECT " + COLUMNS + " FROM tpip_provider_product ORDER BY provider_id,product_name,id", MAPPER)
                : jdbc.query("SELECT " + COLUMNS + " FROM tpip_provider_product WHERE provider_id=? ORDER BY product_name,id", MAPPER, providerId);
    }
    @Override public ProviderProduct create(ProviderProduct product, String actor) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(
                "INSERT INTO tpip_provider_product(provider_id,product_code,product_name,description,status,created_by) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS); statement.setLong(1, product.providerId());
            statement.setString(2, product.productCode().value()); statement.setString(3, product.productName());
            statement.setString(4, product.description()); statement.setString(5, product.status().name());
            statement.setString(6, actor); return statement; }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return provider product id");
        return findById(keys.getKey().longValue()).orElseThrow();
    }
    @Override public Optional<Long> findProductIdByChannel(long channelId) {
        return jdbc.query("SELECT product_id FROM tpip_provider_product_channel WHERE channel_id=?",
                (rs, rowNum) -> rs.getLong(1), channelId).stream().findFirst();
    }
    @Override public Optional<Long> findProductIdByInterface(long contractId) {
        return jdbc.query("SELECT product_id FROM tpip_provider_product_interface WHERE provider_contract_id=?",
                (rs, rowNum) -> rs.getLong(1), contractId).stream().findFirst();
    }
    @Override public List<Long> findInterfaceIds(long productId) {
        return jdbc.query("SELECT provider_contract_id FROM tpip_provider_product_interface WHERE product_id=? ORDER BY provider_contract_id",
                (rs, rowNum) -> rs.getLong(1), productId);
    }
    @Override public void attachChannel(long productId, long channelId, String actor) {
        jdbc.update("INSERT INTO tpip_provider_product_channel(product_id,channel_id,created_by) VALUES(?,?,?)",
                productId, channelId, actor);
    }
    @Override public void attachInterface(long productId, long contractId, String actor) {
        jdbc.update("INSERT IGNORE INTO tpip_provider_product_interface(product_id,provider_contract_id,created_by) VALUES(?,?,?)",
                productId, contractId, actor);
    }
}
