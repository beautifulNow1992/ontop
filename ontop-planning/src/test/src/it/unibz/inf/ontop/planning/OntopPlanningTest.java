package it.unibz.inf.ontop.planning;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class OntopPlanningTest {

    @Test
    public void getSQLForFragments1() throws Exception {
        String query =
                "PREFIX : <http://sws.ifi.uio.no/vocab/npd-v2#>" +
                        "PREFIX nlxv: <http://sws.ifi.uio.no/vocab/norlex#>" +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" +
                        "PREFIX npd: <http://sws.ifi.uio.no/data/npd-v2/>" +
                        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>" +
                        "PREFIX owl: <http://www.w3.org/2002/07/owl#>" +
                        "PREFIX nlx: <http://sws.ifi.uio.no/data/norlex/>" +
                        "PREFIX npdv: <http://sws.ifi.uio.no/vocab/npd-v2#>" +
                        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>" +
                        "SELECT DISTINCT ?wc ?w ?y ?c ?l\n" +
                        "WHERE {\n" +
                        "  ?wc npdv:coreForWellbore ?x .\n" +
                        "  ?x rdf:type npdv:Wellbore .\n" +
                        "  ?x npdv:name ?w .\n" +
                        "\n" +
                        "  ?x npdv:wellboreCompletionYear ?y .\n" +
                        "  ?x npdv:drillingOperatorCompany ?d .\n" +
                        "  ?d npdv:name ?c .\n" +
                        "  ?wc npdv:coresTotalLength ?l .\n" +
                        "}\n";


        List<String> fragments = Lists.newArrayList(query);

        final String owlfile = "src/test/resources/npd-v2-ql_a.owl";
        final String obdafile = "src/test/resources/npd-v2-ql-postgres.obda";

        OntopPlanning op = new OntopPlanning(owlfile, obdafile);

        String sql = op.getSQLForFragments(fragments);

        System.out.println(sql);
    }

    @Test
    public void getSQLForFragments2() throws Exception {

        String fragment1 = "PREFIX npdv: <http://sws.ifi.uio.no/vocab/npd-v2#> \n " +
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
                "SELECT DISTINCT ?wc ?x ?company ?l\n" +
                "WHERE {\n" +
                "   ?wc npdv:coreForWellbore ?x .\n" +
                "   ?x rdf:type npdv:Wellbore .\n" +
                "  ?x npdv:drillingOperatorCompany ?y .\n" +
                "  ?y npdv:name ?company .\n" +
                "  ?wc npdv:coresTotalLength ?l .\n" +
                "}\n";

        String fragment2 = "PREFIX npdv: <http://sws.ifi.uio.no/vocab/npd-v2#> \n " +
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n"
                + "SELECT DISTINCT ?x ?wellbore ?year\n" +
                "WHERE {\n" +
                "  ?x npdv:name ?wellbore .  \n" +
                "   ?x npdv:wellboreCompletionYear ?year .\n" +
                "}";

        List<String> fragments = Lists.newArrayList(fragment1, fragment2);

        final String owlfile = "src/test/resources/npd-v2-ql_a.owl";
        final String obdafile = "src/test/resources/npd-v2-ql-postgres.obda";

        OntopPlanning op = new OntopPlanning(owlfile, obdafile);

        String sql = op.getSQLForFragments(fragments);

        System.out.println(sql);
    }




}