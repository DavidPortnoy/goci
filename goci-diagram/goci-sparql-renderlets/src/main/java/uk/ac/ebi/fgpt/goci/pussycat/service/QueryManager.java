package uk.ac.ebi.fgpt.goci.pussycat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import uk.ac.ebi.fgpt.goci.pussycat.exception.SPARQLQueryException;
import uk.ac.ebi.fgpt.goci.pussycat.utils.PropertiesMapAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by dwelter on 19/05/14.
 */
public class QueryManager {

    private Resource sparqlQueryResource;
    private String[] queries;

    public PropertiesMapAdapter getPropertiesMapAdapter() {
        return propertiesMapAdapter;
    }

    public void setPropertiesMapAdapter(PropertiesMapAdapter propertiesMapAdapter) {
        this.propertiesMapAdapter = propertiesMapAdapter;
    }

    private PropertiesMapAdapter propertiesMapAdapter;

    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    public Resource getSparqlQueryResource() {
        return sparqlQueryResource;
    }

    public void setSparqlQueryResource(Resource sparqlQueryResource) {
        this.sparqlQueryResource = sparqlQueryResource;
    }

    public void init() {
        try {
            this.queries = collectQueries(getSparqlQueryResource().getInputStream());
        }
        catch (IOException e) {
            throw new RuntimeException(
                    "Unable to load SPARQL queries from resource " + getSparqlQueryResource().getDescription(), e);
        }
    }

    public String getSparqlQuery(String queryId, boolean withDefaultPrefix) {
        for (String query : queries) {
            final String name = query.substring(0, query.indexOf(":"));


            if (name.equals(queryId)) {
                String q = "";
                if (withDefaultPrefix) {
                    q = getPrefix() + "\n";
                }
                return q + query.substring(name.length() + 2).trim();
            }
        }

        getLog().error("No query for " + queryId + " found");
        throw new SPARQLQueryException("No SPARQL template query for " + queryId);
    }

    public String getSparqlQuery(String queryId) {
        return getSparqlQuery(queryId, true);
    }

    public String getPrefix() {
        // add prefixes for all loaded namespaces
        StringBuilder sb = new StringBuilder();
        Map<String, String> propMap = getPropertiesMapAdapter().getPropertyMap();
        for (String prefix : propMap.keySet()) {
            sb.append("PREFIX ").append(prefix).append(":<").append(propMap.get(prefix)).append(">\n");
        }
        return sb.toString();
    }

    private String[] collectQueries(InputStream in) throws IOException {
        getLog().debug("Loading SPARQL queries...");
        List<String> queries = new ArrayList<String>();
        BufferedReader inp = new BufferedReader(new InputStreamReader(in));
        String nextLine = null;

        while (true) {
            String line = nextLine;
            nextLine = null;
            if (line == null) {
                line = inp.readLine();
            }
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("^[") && line.endsWith("]")) {
                StringBuilder buff = new StringBuilder(line.substring(2, line.length() - 1));
                buff.append(": ");

                for (; ; ) {
                    line = inp.readLine();
                    if (line == null) {
                        break;
                    }
                    line = line.trim();
                    if (line.length() == 0) {
                        continue;
                    }
                    if (line.startsWith("#")) {
                        continue;
                    }
                    if (line.startsWith("^[")) {
                        nextLine = line;
                        break;
                    }
                    buff.append(line);
                    buff.append(System.getProperty("line.separator"));
                }

                queries.add(buff.toString());
            }
        }

        String[] result = new String[queries.size()];
        for (int i = 0; i < queries.size(); i++) {
            getLog().debug("Adding query '" + queries.get(i) + "'");
            result[i] = queries.get(i);
        }
        return result;
    }

    public String getSparqlCountQuery(String queryId) {
        String subQuery = getSparqlQuery(queryId, false);
        return getPrefix() + "\nSELECT (count(*) as ?count) WHERE { { " + subQuery + " } }";
    }
}

