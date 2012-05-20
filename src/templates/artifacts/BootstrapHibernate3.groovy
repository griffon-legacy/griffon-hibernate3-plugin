import org.hibernate.Session

class BootstrapHibernate3 {
    def init = { String dataSourceName, Session session ->
    }

    def destroy = { String dataSourceName, Session session ->
    }
} 
