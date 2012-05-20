/*
 * Copyright 2011 the original author or authors.
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
package griffon.plugins.hibernate3.internal;

import org.hibernate.HibernateException;
import org.hibernate.connection.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * <p>Hibernate connection provider for local DataSource instances.</p>
 * <p>Based on Spring's {@code org.springframework.orm.hibernate3.LocalDataSourceConnectionProvider}.</p>
 * Original author: Juergen Hoeller (Spring 1.2)
 *
 * @author Andres Almiray
 */
public class DataSourceConnectionProvider implements ConnectionProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DataSourceConnectionProvider.class);
    private DataSource dataSource;

    public DataSource getDataSource() {
        if (null == this.dataSource) {
            this.dataSource = HibernateConfigurationHelper.getDataSource();
        }
        return this.dataSource;
    }

    public void configure(Properties properties) throws HibernateException {
        this.dataSource = HibernateConfigurationHelper.getDataSource();
    }

    public Connection getConnection() throws SQLException {
        try {
            return getDataSource().getConnection();
        } catch (SQLException ex) {
            logExceptions(ex);
            throw ex;
        }
    }

    public void closeConnection(Connection connection) throws SQLException {
        try {
            connection.close();
        } catch (SQLException ex) {
            logExceptions(ex);
            throw ex;
        }
    }

    public void close() throws HibernateException {
    }

    public boolean supportsAggressiveRelease() {
        return true;
    }

    private void logExceptions(Exception x) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(x.getMessage(), x);
        }
    }
}
