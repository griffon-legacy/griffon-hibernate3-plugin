/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package griffon.plugins.hibernate3

import griffon.plugins.datasource.DataSourceConnector
import griffon.plugins.datasource.DataSourceHolder
import griffon.plugins.hibernate3.internal.HibernateConfigurationHelper
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import griffon.util.CallableWithArgs
import griffon.util.ConfigUtils
import griffon.core.GriffonApplication

import javax.sql.DataSource

/**
 * @author Andres Almiray
 */
@Singleton
final class Hibernate3Connector implements Hibernate3Provider {
    private bootstrap

    Object withHibernate3(String sessionFactoryName = 'default', Closure closure) {
        SessionFactoryHolder.instance.withHibernate3(sessionFactoryName, closure)
    }

    public <T> T withHibernate3(String sessionFactoryName = 'default', CallableWithArgs<T> callable) {
        SessionFactoryHolder.instance.withHibernate3(sessionFactoryName, callable)
    }

    // ======================================================

    ConfigObject createConfig(GriffonApplication app) {
        ConfigUtils.loadConfigWithI18n('Hibernate3Config')
    }

    private ConfigObject narrowConfig(ConfigObject config, String dataSourceName) {
        return dataSourceName == 'default' ? config.sessionFactory : config.sessionFactories[dataSourceName]
    }

    SessionFactory connect(GriffonApplication app, String dataSourceName = 'default') {
        if (SessionFactoryHolder.instance.isSessionFactoryAvailable(dataSourceName)) {
            return SessionFactoryHolder.instance.getSessionFactory(dataSourceName)
        }

        ConfigObject dsConfig = DataSourceConnector.instance.createConfig(app)
        if (dataSourceName == 'default') {
            dsConfig.dataSource.schema.skip = true
        } else {
            dsConfig.dataSources."$dataSourceName".schema.skip = true
        }
        DataSource dataSource = DataSourceConnector.instance.connect(app, dsConfig, dataSourceName)

        ConfigObject config = narrowConfig(createConfig(app), dataSourceName)
        app.event('Hibernate3ConnectStart', [config, dataSourceName])
        Configuration configuration = createConfiguration(app, config, dsConfig, dataSourceName)
        createSchema(dsConfig.dbCreate ?: 'create-drop', configuration)
        SessionFactory sessionFactory = configuration.buildSessionFactory()
        SessionFactoryHolder.instance.setSessionFactory(dataSourceName, sessionFactory)
        bootstrap = app.class.classLoader.loadClass('BootstrapHibernate3').newInstance()
        bootstrap.metaClass.app = app
        SessionFactoryHolder.instance.withHibernate3(dataSourceName) { dsName, session -> bootstrap.init(dsName, session) }
        app.event('Hibernate3ConnectEnd', [dataSourceName, dataSource])
        sessionFactory
    }

    void disconnect(GriffonApplication app, String dataSourceName = 'default') {
        if (!SessionFactoryHolder.instance.isSessionFactoryAvailable(dataSourceName)) return

        SessionFactory sessionFactory = SessionFactoryHolder.instance.getSessionFactory(dataSourceName)
        app.event('Hibernate3DisconnectStart', [dataSourceName, sessionFactory])
        SessionFactoryHolder.instance.withHibernate3(dataSourceName) { dsName, session -> bootstrap.destroy(dsName, session) }
        SessionFactoryHolder.instance.disconnectSessionFactory(dataSourceName)
        app.event('Hibernate3DisconnectEnd', [dataSourceName])
        ConfigObject config = DataSourceConnector.instance.createConfig(app)
        DataSourceConnector.instance.disconnect(app, config, dataSourceName)
    }

    private Configuration createConfiguration(GriffonApplication app, ConfigObject config, ConfigObject dsConfig, String dataSourceName) {
        DataSource dataSource = DataSourceHolder.instance.getDataSource(dataSourceName)
        HibernateConfigurationHelper configHelper = new HibernateConfigurationHelper(config, dsConfig, dataSourceName, dataSource)
        Configuration configuration = configHelper.buildConfiguration()
        app.event('Hibernate3ConfigurationAvailable', [[
                configuration: configuration,
                dataSourceName: dataSourceName,
                dataSourceConfig: dsConfig,
                hibernateConfig: config
        ]])
        configuration
    }

    private void createSchema(String dbCreate, Configuration configuration) {
        if (dbCreate == 'skip') dbCreate = 'validate'
        configuration.setProperty('hibernate.hbm2ddl.auto', dbCreate)
    }
}
