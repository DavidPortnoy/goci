package uk.ac.ebi.fgpt.goci.pussycat.controller;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.fgpt.goci.dao.OntologyDAO;
import uk.ac.ebi.fgpt.goci.exception.OWLConversionException;
import uk.ac.ebi.fgpt.goci.lang.OntologyConfiguration;
import uk.ac.ebi.fgpt.goci.lang.OntologyConstants;
import uk.ac.ebi.fgpt.goci.pussycat.exception.PussycatSessionNotReadyException;
import uk.ac.ebi.fgpt.goci.pussycat.manager.PussycatManager;
import uk.ac.ebi.fgpt.goci.pussycat.model.TraitSummary;
import uk.ac.ebi.fgpt.goci.pussycat.renderlet.RenderletNexus;
import uk.ac.ebi.fgpt.goci.pussycat.session.PussycatSession;
import uk.ac.ebi.fgpt.goci.pussycat.session.PussycatSessionStrategy;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Created with IntelliJ IDEA.
 * User: dwelter
 * Date: 24/09/12
 * Time: 14:51
 * To change this template use File | Settings | File Templates.
 */
@Controller
@RequestMapping("/summaries")
public class PussycatSummariesController {
    private PussycatSessionStrategy sessionStrategy;
    private PussycatManager pussycatManager;

    private OntologyConfiguration ontologyConfiguration;

    private OntologyDAO ontologyDAO;

    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    public PussycatSessionStrategy getSessionStrategy() {
        return sessionStrategy;
    }

    @Autowired
    public void setSessionStrategy(PussycatSessionStrategy sessionStrategy) {
        this.sessionStrategy = sessionStrategy;
    }

    public PussycatManager getPussycatManager() {
        return pussycatManager;
    }

    @Autowired
    public void setPussycatSessionManager(PussycatManager pussycatManager) {
        this.pussycatManager = pussycatManager;
    }

    public OntologyConfiguration getOntologyConfiguration() {
        return ontologyConfiguration;
    }

    @Autowired
    public void setOntologyConfiguration(@Qualifier("config") OntologyConfiguration ontologyConfiguration) {
        this.ontologyConfiguration = ontologyConfiguration;
    }

    public OntologyDAO getOntologyDAO() {
        return ontologyDAO;
    }

    @Autowired
    public void setOntologyDAO(OntologyDAO ontologyDAO) {
        this.ontologyDAO = ontologyDAO;
    }

    @RequestMapping(value = "/associations/{associationIds}")
    public @ResponseBody TraitSummary getAssociationSummary(@PathVariable String associationIds,
                                                            HttpSession session)
            throws PussycatSessionNotReadyException, OWLConversionException {
        getLog().debug("Received request to display information for associations " + associationIds);

        ArrayList<String> allIds = new ArrayList<String>();
        if(associationIds.contains(",")){
            StringTokenizer tokenizer = new StringTokenizer(associationIds,",");
            while (tokenizer.hasMoreTokens()){
                String uri = tokenizer.nextToken();
                uri = uri.replace("gwas-diagram:", "http://www.ebi.ac.uk/efo/gwas-diagram/");
                allIds.add(uri);
            }
        }
        else {
            associationIds = associationIds.replace("gwas-diagram:", "http://www.ebi.ac.uk/efo/gwas-diagram/");
            allIds.add(associationIds);
        }

        getLog().debug("This trait represents " + allIds.size() + " different associations");

        return getSummary(allIds, session);
    }


    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(PussycatSessionNotReadyException.class)
    public @ResponseBody
    String handlePussycatSessionNotReadyException(PussycatSessionNotReadyException e) {
        String responseMsg = "Please wait while Pussycat starts up!<br/>" + e.getMessage();
        getLog().error(responseMsg, e);
        return responseMsg;
    }

    protected PussycatSession getPussycatSession(HttpSession session) {
        getLog().debug("Attempting to obtain Pussycat session for HttpSession '" + session.getId() + "'");
        if (getPussycatManager().hasAvailablePussycatSession(session)) {
            getLog().debug("Pussycat manager has an available session for HttpSession '" + session.getId() + "'");
            return getPussycatManager().getPussycatSession(session);
        }
        else {
            PussycatSession pussycatSession;
            if (getSessionStrategy() == PussycatSessionStrategy.JOIN &&
                    getPussycatManager().getPussycatSessions().size() > 0) {
                pussycatSession = getPussycatManager().getPussycatSessions().iterator().next();
            }
            else {
                pussycatSession = getPussycatManager().createPussycatSession();
                getLog().debug("Created new pussycat session, id '" + pussycatSession.getSessionID() + "'");
            }
            getLog().debug("Pussycat manager has no available session, but can join HttpSession " +
                    "'" + session.getId() + "' to pussycat session " +
                    "'" + pussycatSession.getSessionID() + "'");
            return getPussycatManager().bindPussycatSession(session, pussycatSession);
        }
    }

    protected RenderletNexus getRenderletNexus(HttpSession session) throws PussycatSessionNotReadyException {
        getLog().debug("Attempting to obtain RenderletNexus session for HttpSession '" + session.getId() + "'");

        RenderletNexus renderletNexus;
        if (getPussycatManager().hasAvailableRenderletNexus(session)) {
            getLog().debug("RenderletNexus available for HttpSession '" + session.getId() + "'");
            renderletNexus = getPussycatManager().getRenderletNexus(session);
        }
        else {
            renderletNexus = getPussycatManager().createRenderletNexus(
                    getOntologyConfiguration(),
                    getPussycatManager().getPussycatSession(session));
            getPussycatManager().bindRenderletNexus(session, renderletNexus);
        }

        return renderletNexus;
    }

    protected TraitSummary getSummary(ArrayList<String> associationURIs, HttpSession session) throws PussycatSessionNotReadyException, OWLConversionException {
        TraitSummary summary = new TraitSummary();

        OWLReasoner reasoner = getPussycatSession(session).getReasoner();
        OWLOntology ontology = reasoner.getRootOntology();
        OWLDataFactory df = ontologyConfiguration.getOWLDataFactory();

        for(String associationURI : associationURIs){
            getLog().debug("Acquiring information for association " + associationURI);

            String rs_id = null;
            String pm_id = null;
            String pval = null;
            String efoTrait = null;
            String efoUri = null;

            IRI iri = IRI.create(associationURI);
            OWLNamedIndividual association = df.getOWLNamedIndividual(iri);
            getLog().debug("Got the OWL individual " + association);

            if(summary.getGwasTrait() == null){
                OWLDataProperty has_name = df.getOWLDataProperty(IRI.create(OntologyConstants.HAS_GWAS_TRAIT_NAME_PROPERTY_IRI));

                if(association.getDataPropertyValues(has_name,ontology).size() != 0){
                    OWLLiteral name = association.getDataPropertyValues(has_name,ontology).iterator().next();
                    summary.setGwasTrait(name.getLiteral());
                    getLog().debug("The GWAS trait for this association is " + summary.getGwasTrait());
                }
            }

//get the pvalue
            OWLDataProperty has_pval = df.getOWLDataProperty((IRI.create(OntologyConstants.HAS_P_VALUE_PROPERTY_IRI)));
            if(association.getDataPropertyValues(has_pval,ontology).size() != 0){
                OWLLiteral p = association.getDataPropertyValues(has_pval, ontology).iterator().next();
                pval = p.getLiteral();
                getLog().debug("The p-value for this association is " + pval);
            }

//get the SNP and the trait
            OWLObjectProperty is_about = df.getOWLObjectProperty(IRI.create(OntologyConstants.IS_ABOUT_IRI));
            OWLIndividual[] related = association.getObjectPropertyValues(is_about, ontology).toArray(new OWLIndividual[0]);

            IRI snp_class = IRI.create(OntologyConstants.SNP_CLASS_IRI);
            OWLNamedIndividual snp = null;
            OWLNamedIndividual trait = null;

            for(OWLIndividual ind : related){
                boolean isSNP = checkType((OWLNamedIndividual) ind, ontology, snp_class);
                if(isSNP){
                    snp = (OWLNamedIndividual) ind;
                    getLog().debug("The SNP for this association is " + snp);
                }
                else{
                    trait = (OWLNamedIndividual) ind;
                    getLog().debug("The trait for this association is " + trait);
                }
            }

 //get the RS id for the SNP
            if(snp != null){
                OWLDataProperty has_rsID = df.getOWLDataProperty(IRI.create(OntologyConstants.HAS_SNP_REFERENCE_ID_PROPERTY_IRI));
                if(snp.getDataPropertyValues(has_rsID,ontology).size() != 0){
                    OWLLiteral id = snp.getDataPropertyValues(has_rsID,ontology).iterator().next();
                    rs_id = id.getLiteral();
                    getLog().debug("The RS id is " + rs_id);
                }
            }

            if(trait != null){
                Set<OWLClassExpression> allTypes = trait.getTypes(ontology);
                for(OWLClassExpression expr : allTypes){
                    OWLClass typeClass = expr.asOWLClass();
                    IRI typeIRI = typeClass.getIRI();
                    efoUri = typeIRI.toString();
                    efoTrait = ontologyConfiguration.getEfoLabels().get(typeIRI);

                    getLog().debug("The EFO label and URI are " + efoTrait + " and " + efoUri);

                }
            }

//get the Pubmed ID of the study
            OWLObjectProperty part_of = df.getOWLObjectProperty(IRI.create(OntologyConstants.PART_OF_IRI));
            Set<OWLNamedIndividual> studies = reasoner.getObjectPropertyValues(association,part_of).getFlattened();
            OWLDataProperty has_pmid = df.getOWLDataProperty(IRI.create(OntologyConstants.HAS_PUBMED_ID_PROPERTY_IRI));

            for(OWLNamedIndividual study : studies){
                Set<OWLLiteral> pmids = study.getDataPropertyValues(has_pmid, ontology);
                for(OWLLiteral id : pmids){
                    pm_id = id.getLiteral();
                    getLog().debug("The Pubmed id is " + pm_id);

                }
            }

            summary.addSNP(pm_id,rs_id,pval,efoTrait,efoUri);

        }

        return summary;

    }

    public boolean checkType(OWLNamedIndividual individual, OWLOntology ontology, IRI typeIRI) {
        boolean type = false;
        OWLClassExpression[] allTypes = individual.getTypes(ontology).toArray(new OWLClassExpression[0]);

        for (int i = 0; i < allTypes.length; i++) {
            OWLClass typeClass = allTypes[i].asOWLClass();

            if (typeClass.getIRI().equals(typeIRI)) {
                type = true;
                break;
            }
        }
        return type;

    }
}