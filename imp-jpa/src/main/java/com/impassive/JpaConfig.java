package com.impassive;

import com.google.common.collect.Lists;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.mode.repository.standalone.StandalonePersistRepositoryConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.ShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionManager;

/**
 * @author impassive
 */
@Configuration
@ComponentScan
@EnableJpaRepositories(basePackages = "com.impassive.repository")
public class JpaConfig {

  @Bean
  public List<TableConfig> tableConfigs() {
    List<TableConfig> tableConfigs = new ArrayList<>();
    tableConfigs.add(new TableConfig(
        "buyDataSource",
        "test_shard",
        "external_id",
        2
    ));

/*    tableConfigs.add(new TableConfig(
        "tagDataSource",
        "test_table_tag",
        "external_id",
        1
    ));*/

    return tableConfigs;
  }


  @Bean
  public List<RuleConfiguration> ruleConfigurations(List<TableConfig> tableConfigs) {
    ShardingRuleConfiguration tableRule = getOrderTableRuleConfiguration(tableConfigs);
    return Lists.newArrayList(tableRule);
  }

  @Bean
  public DataSource shardingSphereDataSource(
      DataSource tagDataSource,
      DataSource buyDataSource,
      List<RuleConfiguration> ruleConfigurations
  ) throws SQLException {
    Map<String, DataSource> dataSourceMap = new HashMap<>();
    dataSourceMap.put("tagDataSource", tagDataSource);
    dataSourceMap.put("buyDataSource", buyDataSource);

    /*Properties props = new Properties();
    props.setProperty("provider", "H2");
    props.setProperty("jdbc_url",
        "jdbc:h2:tcp://localhost/~/tmp/h2/config;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;");*/

    // ?????????????????? ????????????????????? h2 ??? memory ??????
    // JDBC ??? ?????? H2
    StandalonePersistRepositoryConfiguration repository = new StandalonePersistRepositoryConfiguration(
        "JDBC", new Properties());

    ModeConfiguration modeConfiguration = new ModeConfiguration("Standalone", repository);
    return ShardingSphereDataSourceFactory.createDataSource(
        "shardingSphere-datasource",
        modeConfiguration,
        dataSourceMap,
        ruleConfigurations,
        new Properties()
    );
  }

  @Bean
  public DataSource tagDataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
    dataSource.setUrl(
        "jdbc:mysql://10.200.68.3:3306/dev_tag?characterEncoding=utf-8&useUnicode=true&zeroDateTimeBehavior=convertToNull&useCursorFetch=true");
    dataSource.setUsername("adm");
    dataSource.setPassword("oK1@cM2]dB2!");
    return dataSource;
  }

  @Bean
  public DataSource buyDataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
    dataSource.setUrl(
        "jdbc:mysql://10.200.68.3:3306/dev_buy?characterEncoding=utf-8&useUnicode=true&zeroDateTimeBehavior=convertToNull&useCursorFetch=true");
    dataSource.setUsername("adm");
    dataSource.setPassword("oK1@cM2]dB2!");
    return dataSource;
  }

  @Bean
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(
      DataSource shardingSphereDataSource) {
    LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
    bean.setPackagesToScan("com.impassive.entity");
    bean.setDataSource(shardingSphereDataSource);
    HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
    jpaVendorAdapter.setGenerateDdl(false);
    jpaVendorAdapter.setShowSql(true);
    Properties jpaProperties = new Properties();
    jpaProperties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL57Dialect");
    //jpaProperties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
    bean.setJpaProperties(jpaProperties);
    bean.setJpaVendorAdapter(jpaVendorAdapter);
    return bean;
  }

  /**
   * ??????????????????????????? ????????????????????????  getObject ,?????? ?????? getNativeEntityManagerFactory???????????????????????????????????????????????????????????? ????????????
   * <code>
   * EntityManagerFactory nativeEntityManagerFactory = emfBean.getNativeEntityManagerFactory();
   * EntityManager entityManager = nativeEntityManagerFactory.createEntityManager();
   * entityManager.getTransaction().begin(); AtlasTagExtra entity = new AtlasTagExtra();
   * entity.setAtlasId(2L); entity.setCreateAt(System.currentTimeMillis()); entity.setRemarks("x");
   * entity.setUpdateAt(System.currentTimeMillis()); entity.setStatus(3);
   * entityManager.persist(entity); entityManager.getTransaction().commit();
   * </code>
   */
  @Bean
  public TransactionManager transactionManager(
      LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
    JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
    jpaTransactionManager.setEntityManagerFactory(entityManagerFactoryBean.getObject());
    return jpaTransactionManager;
  }


  /* ----------------------- */

  private ShardingRuleConfiguration getOrderTableRuleConfiguration(
      List<TableConfig> tableConfigs
  ) {
    ShardingRuleConfiguration shardingRuleConfiguration = new ShardingRuleConfiguration();

    // ?????? ?????? ??????
    // ???????????????????????????????????????
    Properties props = new Properties();
    List<ShardingTableRuleConfiguration> strcList = new ArrayList<>();
    for (TableConfig tableConfig : tableConfigs) {
      props.setProperty(tableConfig.magicTableName(), tableConfig.shardCnt() + "");
      strcList.add(buildTableShard(tableConfig));
    }
    AlgorithmConfiguration value = new AlgorithmConfiguration("impassive", props);
    // ?????? my_own ?????????????????????????????????????????????????????????????????????????????????
    shardingRuleConfiguration.getShardingAlgorithms().put("my_own", value);
    // ????????????????????????
    shardingRuleConfiguration.setTables(strcList);

    return shardingRuleConfiguration;
  }

  private ShardingTableRuleConfiguration buildTableShard(TableConfig tableConfig) {
    // table ???????????????
    String actualDataNode;
    if (tableConfig.shardCnt() > 1) {
      actualDataNode = String.format(
          "%s.%s_${[0,%s]}",
          tableConfig.database(),
          tableConfig.magicTableName(),
          tableConfig.shardCnt() - 1);
    } else {
      actualDataNode = String.format("%s.%s", tableConfig.database(), tableConfig.magicTableName());
    }

    ShardingTableRuleConfiguration strc = new ShardingTableRuleConfiguration(
        tableConfig.magicTableName(),
        actualDataNode
    );
    ShardingStrategyConfiguration tableShardingStrategy = new StandardShardingStrategyConfiguration(
        tableConfig.shardColumn(),
        "my_own"
    );

    strc.setTableShardingStrategy(tableShardingStrategy);

    return strc;
  }

}
