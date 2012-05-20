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

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.tool.hbm2ddl.DatabaseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Executes schema scripts.
 * Based on Spring's {@code org.springframework.orm.hibernate3.LocalSessionFactoryBean}
 * Original author: Juergen Hoeller (Spring 1.2)
 *
 * @author Andres Almiray
 */
public class HibernateSchemaHelper {
    private static final Logger LOG = LoggerFactory.getLogger(HibernateSchemaHelper.class);
    private final Configuration configuration;
    private final SessionFactory sessionFactory;

    public interface HibernateCallback {
        void doInSession(Session session) throws SQLException;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public HibernateSchemaHelper(Configuration configuration, SessionFactory sessionFactory) {
        this.configuration = configuration;
        this.sessionFactory = sessionFactory;
    }

    public void dropDatabaseSchema() {
        if (LOG.isDebugEnabled()) {
            LOG.info("Dropping database schema for Hibernate SessionFactory");
        }
        execute(
                new HibernateCallback() {
                    public void doInSession(Session session) throws SQLException {
                        Connection con = session.connection();
                        Dialect dialect = Dialect.getDialect(getConfiguration().getProperties());
                        String[] sql = getConfiguration().generateDropSchemaScript(dialect);
                        executeSchemaScript(con, sql);
                    }
                }
        );
    }

    public void createDatabaseSchema() {
        if (LOG.isDebugEnabled()) {
            LOG.info("Creating database schema for Hibernate SessionFactory");
        }
        execute(
                new HibernateCallback() {
                    public void doInSession(Session session) throws SQLException {
                        Connection con = session.connection();
                        Dialect dialect = Dialect.getDialect(getConfiguration().getProperties());
                        String[] sql = getConfiguration().generateSchemaCreationScript(dialect);
                        executeSchemaScript(con, sql);
                    }
                }
        );
    }

    public void updateDatabaseSchema() {
        if (LOG.isDebugEnabled()) {
            LOG.info("Updating database schema for Hibernate SessionFactory");
        }
        execute(
                new HibernateCallback() {
                    public void doInSession(Session session) throws SQLException {
                        session.setFlushMode(FlushMode.AUTO);
                        Connection con = session.connection();
                        Dialect dialect = Dialect.getDialect(getConfiguration().getProperties());
                        DatabaseMetadata metadata = new DatabaseMetadata(con, dialect);
                        String[] sql = getConfiguration().generateSchemaUpdateScript(dialect, metadata);
                        executeSchemaScript(con, sql);
                    }
                }
        );
    }

    private void execute(HibernateCallback callback) {
        Session session = sessionFactory.getCurrentSession();
        try {
            callback.doInSession(session);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            session.close();
        }
    }

    private void executeSchemaScript(Connection con, String[] sql) throws SQLException {
        if (sql != null && sql.length > 0) {
            boolean oldAutoCommit = con.getAutoCommit();
            if (!oldAutoCommit) {
                con.setAutoCommit(true);
            }
            try {
                Statement stmt = con.createStatement();
                try {
                    for (String sqlStmt : sql) {
                        executeSchemaStatement(stmt, sqlStmt);
                    }
                } finally {
                    closeStatement(stmt);
                }
            } finally {
                if (!oldAutoCommit) {
                    con.setAutoCommit(false);
                }
            }
        }
    }

    private void executeSchemaStatement(Statement stmt, String sql) throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Executing schema statement: " + sql);
        }
        try {
            stmt.executeUpdate(sql);
        } catch (SQLException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Unsuccessful schema statement: " + sql, ex);
            }
        }
    }

    private void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException ex) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Could not close JDBC Statement", ex);
                }
            } catch (Throwable ex) {
                if (LOG.isTraceEnabled()) {
                    // We don't trust the JDBC driver: It might throw RuntimeException or Error.
                    LOG.trace("Unexpected exception on closing JDBC Statement", ex);
                }
            }
        }
    }
}
