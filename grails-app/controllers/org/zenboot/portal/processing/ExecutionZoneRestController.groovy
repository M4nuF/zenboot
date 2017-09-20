package org.zenboot.portal.processing

import grails.converters.JSON
import grails.converters.XML
import grails.plugin.springsecurity.SpringSecurityUtils
import groovy.util.slurpersupport.NodeChild
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException
import org.codehaus.groovy.grails.web.json.JSONException
import org.codehaus.groovy.grails.web.json.JSONObject
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.http.HttpStatus
import org.zenboot.portal.AbstractRestController
import org.zenboot.portal.ControllerUtils
import org.zenboot.portal.Host
import org.zenboot.portal.HostState
import org.zenboot.portal.security.Person
import org.zenboot.portal.security.Role
import org.zenboot.portal.Template

class ExecutionZoneRestController extends AbstractRestController implements ApplicationEventPublisherAware{

    def springSecurityService
    def accessService
    def scriptDirectoryService
    def executionZoneService
    def grailsLinkGenerator
    def applicationEventPublisher

    static allowedMethods = [index: "GET" , help: "GET", list: "GET", execute: "POST", listparams: "GET", listactions: "GET", createzone: "POST", exectypes: "GET", execzonetemplate: "GET",
    cloneexecutionzone: "GET", listhosts: "GET", listhoststates: "GET"]

    @Override
    void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.applicationEventPublisher = eventPublisher
    }

    /**
     * The method gives you an overview about the possible rest endpoints and which parameters could be set.
     */
    def help = {
        withFormat {
            xml {
                def builder = new StreamingMarkupBuilder()
                builder.encoding = 'UTF-8'

                String restendpoints = builder.bind {
                    restendpoints {
                        restendpoint {
                            name 'execute'
                            description 'The method execute the specific action of an execution zone based on the parameters.'
                            url '/rest/v1/executionzones/{execId}/actions/{execAction}/execute'
                            exampleurl '/rest/v1/executionzones/1/actions/internal/execute'
                            execId {
                                description 'The id of the specific execution zone.'
                                type 'Long'
                                mandatory 'Yes'
                            }
                            execAction {
                                description 'The name of the action.'
                                type 'String'
                                mandatory 'Yes'
                            }
                            parameters 'Requires json or xml where all the necessary parameters are stored. You can save the result of /listparams to get a working template.'
                        }
                        restendpoint {
                            name 'list'
                            description 'The method returns the execution zones of the user.'
                            urls {
                                all '/rest/v1/executionzones/list'
                                specific '/rest/v1/executionzones/list?execType={execType}'
                                exampleurl '/rest/v1/executionzones/list?execType=internal'
                            }
                            execType {
                                description 'The id or the name of the execution zone type. If not set the method returns all enabled execution zones of the user.'
                                type 'Long (id) or String (name).'
                            }
                        }
                        restendpoint {
                            name 'listparams'
                            description 'The method returns all required parameters on an specific execution zone action.'
                            url '/rest/v1/executionzones/{execId}/actions/{execAction}/listparams'
                            exampleurl '/rest/v1/executionzones/1/actions/sanitycheck/listparams'
                            execId {
                                description 'The id of the specific execution zone.'
                                type 'Long'
                                mandatory 'Yes'
                            }
                            execAction {
                                description 'The name of the action.'
                                type 'String'
                                mandatory 'Yes'
                            }
                        }
                        restendpoint {
                            name 'listactions'
                            description 'The method return all action names of the specific execution zone.'
                            url '/rest/v1/executionzones/$execId/listactions'
                            exampleurl '/rest/v1/executionzones/1/listactions'
                            execId {
                                description 'The id of the specific execution zone.'
                                type 'Long'
                                mandatory 'Yes'
                                }
                        }
                        restendpoint {
                            name 'exectypes'
                            description 'The method return all available execution zone types.'
                            url '/rest/v1/executionzones/exectypes'
                            exampleurl '/rest/v1/executionzones/exectypes'
                        }
                        restendpoint {
                            name 'execzonetemplate'
                            description 'The method return a template of an execution zone which could be used to create a new one.'
                            url '/rest/v1/executionzones/execzonetemplate'
                            exampleurl '/rest/v1/executionzones/execzonetemplate'
                            restriction 'admin only'
                        }
                        restendpoint {
                            name 'createzone'
                            description 'The method return a template of an execution zone which could be used to create a new one.'
                            url '/rest/v1/executionzones/create'
                            exampleurl '/rest/v1/executionzones/create'
                            restriction 'admin only'
                            parameters 'Requires json or xml where all the necessary parameters are stored. You can save the result of /execzonetemplate to get a working template.'
                        }
                        restendpoint {
                            name 'cloneexecutionzone'
                            description 'The method clones an existing execution zone.'
                            url '/rest/v1/executionzones/{execId}/clone'
                            exampleurl '/rest/v1/executionzones/1/clone'
                            restriction 'admin only'
                            execId {
                                description 'The id of the specific execution zone.'
                                type 'Long'
                                mandatory 'Yes'
                            }
                        }
                    }
                }

                def xml = XmlUtil.serialize(restendpoints).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                render contentType: "text/xml", xml
            }
            json {

                def execId = [description: 'The id of the specific execution zone.', type: 'Long', mandatory: 'Yes']
                def execAction = [description: 'The name of the action.', type: 'String', mandatory: 'Yes']
                def execType = [description: 'The id or the name of the execution zone type. If not set the method returns all enabled execution zones of the user.', type: 'Long or String.',
                                mandatory: 'No']

                def executeEndPoint = [description: 'The method execute the specific action of an execution zone based on the parameters.',
                                       parameters: 'Requires json or xml where all the necessary parameters are stored. You can save the result of /listparams to get a working template.']
                def listEndPoint = [description: 'The method returns the execution zones of the user.', execType: execType]
                def listparamsEndPoint = [description: 'The method returns all required parameters on an specific execution zone action.', execId: execId, action: execAction]
                def listactionsEndPoint = [description: 'The method return all action names of the specific execution zone.', execId: execId]
                def exectypes = [description: 'The method return all available execution zone types.']
                def execzonetemplate = [description: 'The method return a template of an execution zone which could be used to create a new one.', restriction: 'admin only']
                def createzone = [description: 'The method return a template of an execution zone which could be used to create a new one.', restriction: 'admin only']
                def cloneexecutionzone = [description: 'The method clones an existing execution zone.', restriction: 'admins only', execId: execId]

                render (contentType: "text/json") { restendpoints execute: executeEndPoint, list: listEndPoint, listparams: listparamsEndPoint, listactions: listactionsEndPoint,
                        exectypes: exectypes, execzonetemplate: execzonetemplate, create: createzone, clone: cloneexecutionzone }
            }
        }
    }

    /**
     * Returns a list of enabled execution zones to which the user has access.
     * The list is be more specified if an execType param is set.
     */
    def list = {
        def results
        ExecutionZoneType executionZoneType

        if (params.execType) {
            if (params.long('execType')) {
                executionZoneType = ExecutionZoneType.findById(params.execType as Long)
            } else if (params.execType instanceof String) {
                executionZoneType = ExecutionZoneType.findByName(params.execType as String)
            }
            else {
                this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'The executionZoneType (execType) has to be a long or a string')
                return
            }
        }

        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN)) {

            if (executionZoneType) {
                results = ExecutionZone.findAllByTypeAndEnabled(executionZoneType, true)
            }
            else {
                results = ExecutionZone.findAllByEnabled(true)
            }
        }
        else {

            List<ExecutionZone> executionZones = new ArrayList<ExecutionZone>()

            Map executionZonesIDs
            Long currentUserID = springSecurityService.getCurrentUserId() as Long

            if (accessService.accessCache[currentUserID]) {
                executionZonesIDs = accessService.accessCache[currentUserID].findAll {it.value}
            }
            else {
                accessService.refreshAccessCacheByUser(Person.findById(currentUserID))
                executionZonesIDs = accessService.accessCache[currentUserID].findAll {it.value}
            }

            executionZonesIDs.each {
                executionZones.add(ExecutionZone.get(it.key as Long))
            }

            if (executionZoneType) {
                results = new ArrayList<ExecutionZone>()

                executionZones.each {zone ->
                    if (zone.type == executionZoneType && zone.enabled) {
                        results.add(zone)
                    }
                }
            }
            else if (executionZoneType == null && params.execType) {
                this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'The requested execution zone type does not exist.')
                return
            }
            else {
                results = executionZones.findAll() {it.enabled}
            }
        }

        def executionZones = results.collect {[execId: it.id, execType: it.type.name, execDescription: it.description]}

        withFormat {
            xml {
                def builder = new StreamingMarkupBuilder()
                builder.encoding = 'UTF-8'

                String zones = builder.bind {
                    executionzones {
                        executionZones.each { execZone ->
                            executionzone {
                                execId execZone.execId
                                execType execZone.execType
                                execDescription execZone.execDescription
                            }
                        }
                    }
                }

                def xml = XmlUtil.serialize(zones).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                render contentType: "text/xml", xml
            }
            json {
                def zones = [:]
                zones.put('executionZones', executionZones)

                render(contentType: "text/json") { zones } as JSON
            }
        }
    }

    /**
     * The method returns a list of all required parameters of an execution zone.
     */
    def listparams = {

        ExecutionZone executionZone
        String actionName

        if (params.execId) {
            if(ExecutionZone.findById(params.execId as Long)){
                executionZone = ExecutionZone.findById(params.execId as Long)
            }
            else {
                this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'ExecutionZone with id ${params.execId} not found.')
                return
            }
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'ExecutionZone id (execId) not set.')
            return
        }

        if (params.execAction) {
            actionName = params.execAction
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'Action name (execAction) not set.')
            return
        }

        if ( SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN) || userHasAccess(executionZone)) {

            File stackDir = new File(scriptDirectoryService.getZenbootScriptsDir().getAbsolutePath()
                    + "/" + executionZone.type.name + "/scripts/" + actionName)

            if(!isValidScriptDir(stackDir)) {
                return
            }

            def paramsSet = executionZoneService.getExecutionZoneParameters(executionZone, stackDir)

            withFormat {
                xml {
                    def builder = new StreamingMarkupBuilder()
                    builder.encoding = 'UTF-8'

                    String parameters = builder.bind {
                        parameters {
                            execId executionZone.id
                            execAction actionName
                            paramsSet.each { param ->
                                parameter {
                                    parameterName param.name
                                    parameterValue param.value
                                }
                            }
                        }
                    }

                    def xml = XmlUtil.serialize(parameters).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                    xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                    render contentType: "text/xml", xml
                }
                json {
                    def responseParams = [:]
                    responseParams.put('execId', executionZone.id)
                    responseParams.put('execAction', actionName)
                    responseParams.put('parameters', paramsSet.collect {['parameterName': it.name, 'parameterValue': it.value]} )

                    render (contentType: "text/json") { responseParams } as JSON
                }
            }
        }
        else {
            this.renderRestResult(HttpStatus.FORBIDDEN, null, null, 'This user has no permission to request the parameter for this zone.')
        }
    }

    /**
     * This method returns a list of all possible actions for the executionzone.
     */
    def listactions = {

        ExecutionZone executionZone
        File scriptDir

        if (params.execId) {
            if(ExecutionZone.findById(params.execId as Long)){
                executionZone = ExecutionZone.findById(params.execId as Long)
            }
            else {
                this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'ExecutionZone with id ${params.execId} not found.')
                return
            }
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'ExecutionZone id (execId) not set.')
            return
        }

        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN)) {
            scriptDir = new File(scriptDirectoryService.getZenbootScriptsDir().getAbsolutePath()
                    + "/" + executionZone.type.name + "/scripts/" )
        }
        else if (userHasAccess(executionZone)) {

            scriptDir = new File(scriptDirectoryService.getZenbootScriptsDir().getAbsolutePath()
                    + "/" + executionZone.type.name + "/scripts/" )
        }
        else {
            this.renderRestResult(HttpStatus.FORBIDDEN, null, null, 'This user has no permission to request the actions for this zone.')
            return
        }

        if(!isValidScriptDir(scriptDir)) {
            return
        }

        File[] scriptDirFiles = scriptDir.listFiles()

        withFormat {
            xml {
                def builder = new StreamingMarkupBuilder()
                builder.encoding = 'UTF-8'

                String execActions = builder.bind {
                    execActions {
                        scriptDirFiles.each {
                            execAction it.name
                        }
                    }
                }
                def xml = XmlUtil.serialize(execActions).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                render contentType: "text/xml", xml
            }
            json {
                def dirContent = [:]
                dirContent.put('execActions', scriptDirFiles.collect {it.name})

                render (contentType: "text/json") { dirContent } as JSON
            }
        }
    }

    /**
     * execTypes returns a list of all existing executionZoneTypes.
     */
    def exectypes = {
        withFormat {
            xml {
                def builder = new StreamingMarkupBuilder()
                builder.encoding = 'UTF-8'

                String executionZoneTypes = builder.bind {
                    executionZoneTypes {
                        ExecutionZoneType.list().sort().each {
                            executionZoneType it
                        }
                    }
                }

                def xml = XmlUtil.serialize(executionZoneTypes).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                render contentType: "text/xml", xml
            }
            json {
                render (contentType: "text/json") { ExecutionZoneType.list().sort() } as JSON
            }
        }
    }

    /**
     * This method returns a xml or json template to create an execution zone.
     */
    def execzonetemplate = {
        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN)) {
            String[] nonrelevant_Properties = ['actions', 'creationDate', 'hosts', 'templates', 'processingParameters']
            DefaultGrailsDomainClass d = new DefaultGrailsDomainClass(ExecutionZone.class)
            GrailsDomainClassProperty[] properties = d.getPersistentProperties()

            withFormat {
                xml {
                    def builder = new StreamingMarkupBuilder()
                    builder.encoding = 'UTF-8'

                    String executionZone = builder.bind {
                        executionZone {
                            executionZoneProperties {
                                properties.each { property ->
                                    if (!nonrelevant_Properties.contains(property.name)) {
                                        executionZoneProperty {
                                            propertyName property.name
                                            propertyValue ''
                                        }
                                    }
                                }
                            }
                            processingParameters {
                                parameter {
                                    parameterName ''
                                    parameterValue ''
                                    parameterDescription ''
                                    parameterExposed ''
                                    parameterPublished ''
                                }
                            }
                        }
                    }

                    def xml = XmlUtil.serialize(executionZone).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                    xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                    render contentType: "text/xml", xml
                }
                json {
                    def executionzonetemplate = [:]

                    def executionZoneProperties = properties.findAll { !nonrelevant_Properties.contains(it.name) }
                    executionzonetemplate.put('executionZoneProperties', executionZoneProperties.collect {
                        [propertyName: it.name, propertyValue: '']
                    })
                    executionzonetemplate.put('processingParameters', [[parameterName: '', parameterValue: '', parameterDescription: '', parameterExposed: '', parameterPublished: '']])

                    render(contentType: 'text/json') { executionzonetemplate } as JSON
                }
            }
        }
        else {
            this.renderRestResult(HttpStatus.UNAUTHORIZED, null, null, 'You have no permissions to request a execution zone template.')
        }
    }

    /**
     * This method creates a new execution zone.
     */
    def createzone = {
        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN)) {
            Boolean hasError = Boolean.FALSE
            HashMap parameters = new HashMap()
            Map processingParams = [:]

            request.withFormat {
                xml {
                    def xml
                    try {
                        xml = request.XML
                    }
                    catch (ConverterException e) {
                        this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, e.message)
                        hasError = Boolean.TRUE
                        return
                    }

                    def xmlExecutionZoneProperties = xml[0].children.findAll { it.name == 'executionZoneProperties' }

                    xmlExecutionZoneProperties.each { node ->
                        node.children.each { innerNode ->
                            def name = ''
                            def value = ''
                            innerNode.children.each {
                                if (it.name == 'propertyName') {
                                    name = it.text()
                                } else if (it.name == 'propertyValue') {
                                    value = it.text()
                                }
                            }
                            parameters[name] = value
                        }
                    }

                    def xmlExecutionZoneParameters = xml[0].children.findAll { it.name == 'processingParameters' }

                    String[] keys = new String[xmlExecutionZoneParameters.size()]
                    String[] values = new String[xmlExecutionZoneParameters.size()]
                    String[] descriptions = new String[xmlExecutionZoneParameters.size()]
                    String[] exposed = new String[xmlExecutionZoneParameters.size()]
                    String[] published = new String[xmlExecutionZoneParameters.size()]

                    xmlExecutionZoneParameters.eachWithIndex { processingParameters, index ->
                        processingParameters.children.each { parameter ->
                            parameter.children.each {
                                if ('parameterName' == it.name) {
                                    keys[index] = it.text()
                                } else if ('parameterValue' == it.name) {
                                    values[index] = it.text()
                                } else if ('parameterDescription' == it.name) {
                                    descriptions[index] = it.text()
                                } else if ('parameterExposed' == it.name) {
                                    String exposedText = it.text()

                                    if ('true' == exposedText.toLowerCase() || 'false' == exposedText.toLowerCase()) {
                                        exposed[index] = exposedText.toLowerCase()
                                    } else if (exposedText.isEmpty()) {
                                        exposed[index] = 'false'
                                    } else {
                                        renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'Invalid value. parameterExposed has to be true or false.')
                                        hasError = Boolean.TRUE
                                        return
                                    }
                                } else if ('parameterPublished' == it.name) {
                                    String publishedText = it.text()

                                    if ('true' == publishedText.toLowerCase() || 'false' == publishedText.toLowerCase()) {
                                        published[index] = publishedText.toLowerCase()
                                    } else if (publishedText.isEmpty()) {
                                        published[index] = 'false'
                                    } else {
                                        renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'Invalid value. parameterPublished has to be true or false.')
                                        hasError = Boolean.TRUE
                                        return
                                    }
                                }
                            }
                        }
                    }

                    processingParams.put('parameters.key', keys)
                    processingParams.put('parameters.value', values)
                    processingParams.put('parameters.exposed', exposed)
                    processingParams.put('parameters.published', published)
                    processingParams.put('parameters.description', descriptions)
                }
                json {
                    String text = request.getReader().text
                    def json

                    try {
                        json = new JSONObject(text)
                    }
                    catch (JSONException e) {
                        this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, e.getMessage())
                        hasError = Boolean.TRUE
                        return
                    }

                    if (json.executionZoneProperties) {
                        json.executionZoneProperties.each {
                            parameters[it.propertyName] = it.propertyValue
                        }
                    }

                    if (json.processingParameters) {

                        String[] keys = new String[json.processingParameters.size()]
                        String[] values = new String[json.processingParameters.size()]
                        String[] descriptions = new String[json.processingParameters.size()]
                        String[] exposed = new String[json.processingParameters.size()]
                        String[] published = new String[json.processingParameters.size()]

                        json.processingParameters.eachWithIndex { parameter, int index ->
                            parameter.each {
                                if ('parameterName' == it.key) {
                                    keys[index] = it.value
                                } else if ('parameterValue' == it.key) {
                                    values[index] = it.value
                                } else if ('parameterDescription' == it.key) {
                                    descriptions[index] = it.value
                                } else if ('parameterExposed' == it.key) {
                                    String exposedText = it.value

                                    if ('true' == exposedText.toLowerCase() || 'false' == exposedText.toLowerCase()) {
                                        exposed[index] = exposedText.toLowerCase()
                                    } else if (exposedText.isEmpty()) {
                                        exposed[index] = 'false'
                                    } else {
                                        renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'Invalid value. parameterExposed has to be true or false.')
                                        hasError = Boolean.TRUE
                                        return
                                    }
                                } else if ('parameterPublished' == it.key) {
                                    String publishedText = it.value

                                    if ('true' == publishedText.toLowerCase() || 'false' == publishedText.toLowerCase()) {
                                        published[index] = publishedText.toLowerCase()
                                    } else if (publishedText.isEmpty()) {
                                        published[index] = 'false'
                                    } else {
                                        renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'Invalid value. parameterPublished has to be true or false.')
                                        hasError = Boolean.TRUE
                                        return
                                    }
                                }
                            }
                        }

                        processingParams.put('parameters.key', keys)
                        processingParams.put('parameters.value', values)
                        processingParams.put('parameters.exposed', exposed)
                        processingParams.put('parameters.published', published)
                        processingParams.put('parameters.description', descriptions)
                    }
                }
            }

            if (hasError) {
                return
            }

            if (parameters['type'] instanceof String) {
                parameters['type'] = ExecutionZoneType.findByName(parameters['type'] as String).id
            }

            ExecutionZone newExecutionZone = new ExecutionZone(parameters)
            ControllerUtils.synchronizeProcessingParameters(ControllerUtils.getProcessingParameters(processingParams), newExecutionZone)

            if (!newExecutionZone.save(flush: true)) {
                renderRestResult(HttpStatus.INTERNAL_SERVER_ERROR, null, null, 'ERROR. ExecutionZone could not be saved. '
                        + newExecutionZone.errors.allErrors.join(' \n'))
            }

            withFormat {
                xml {
                    render newExecutionZone as XML
                }
                json {
                    JSON.use('deep')
                    render newExecutionZone as JSON
                }
            }
        }
        else {
            this.renderRestResult(HttpStatus.UNAUTHORIZED, null, null, 'You have no permissions to create an execution zone.')
        }
    }

    /**
     * This method clones an exiting execution zone.
     */
    def cloneexecutionzone = {
        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN)) {

            ExecutionZone executionZone
            ExecutionZone clonedExecutionZone

            if (params.execId) {
                executionZone = ExecutionZone.findById(params.execId as Long)
            }
            else {
                this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'The parameter execId to find the execution zone by id is missing.')
                return
            }

            if (executionZone) {
                clonedExecutionZone = new ExecutionZone(executionZone.properties)
                clonedExecutionZone.actions = []
                clonedExecutionZone.hosts = []
                clonedExecutionZone.processingParameters = [] as SortedSet
                clonedExecutionZone.templates = [] as SortedSet

                executionZone.processingParameters.each {
                    ProcessingParameter clonedParameter = new ProcessingParameter(it.properties)
                    clonedExecutionZone.processingParameters.add(clonedParameter)
                }

                executionZone.templates.each {
                    Template template = new Template(it.properties)
                    clonedExecutionZone.templates.add(template)
                }

            }
            else {
                this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'The execution zone for id ' + params.execId + ' could not be found.')
                return
            }

            if (!clonedExecutionZone.save(flush: true)) {
                renderRestResult(HttpStatus.INTERNAL_SERVER_ERROR, null, null, 'ERROR. ExecutionZone could not be saved. '
                        + clonedExecutionZone.errors.allErrors.join(' \n'))
            }

            withFormat {
                xml {
                    render clonedExecutionZone as XML
                }
                json {
                    JSON.use('deep')
                    render clonedExecutionZone as JSON
                }
            }
        }
        else {
            this.renderRestResult(HttpStatus.UNAUTHORIZED, null, null, 'You have no permissions to clone execution zones.')
        }
    }

    /**
     * This method execute actions in zenboot. The 'quantity' parameter ensure that the caller of this method is aware of the number of runs. The 'runs' parameter execute an action 'runs'
     * times. If a required (empty in the original) parameter in the data is set multiple times, the number of executions is the count of this parameter. Are there multiple required
     * parameters the number of them have to be equal or except one they have the count '1'. The 'runs' parameter will be ignored in this case but the number of required parameters have to
     * be the same as the 'quantity' parameter in the url. The 'runs' parameter is usefull if you need the same execution multiple times. This means if all required parameters have the count
     * '1', the number of executions is 'runs' times. Also in this case the count of 'runs' and the 'quantity' have to be the same. Per default 'runs' is set to 1. For more detailed
     * information read the documentation in the wiki.
     */
    def execute = {
        ExecutionZone executionZone
        String executionZoneAction
        Map<String, List> parameters =[:]
        def referralsCol = []
        Boolean hasError = Boolean.FALSE
        int runs = 1
        int quantity

        if (params.execId) {
            if(ExecutionZone.findById(params.execId as Long)){
                executionZone = ExecutionZone.findById(params.execId as Long)
            }
            else {
                this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'ExecutionZone with id ${params.execId} not found.')
                return
            }
        }
        else {
            this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'ExecutionZone id (execId) not set.')
            return
        }

        if (params.execAction) {
            executionZoneAction = params.execAction
        }
        else {
            this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'Action name (execAction) not set.')
            return
        }

        if(params.quantity) {
            if(params.quantity.isInteger()) {
                quantity = params.int('quantity')
            }
            else {
                this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'The quanitiy has to be an integer.')
            }
        }
        else {
            this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'The quanitiy which ensure that the number of executions is like you expect is missing.')
            return
        }

        if(params.runs) {
            if(params.runs.isInteger()) {
                runs = params.int('runs')
            }
        }

        File stackDir = new File(scriptDirectoryService.getZenbootScriptsDir().getAbsolutePath()
                + "/" + executionZone.type.name + "/scripts/" + executionZoneAction)

        if(!isValidScriptDir(stackDir)) {
            return
        }

        Set<ProcessingParameter> origin_params = executionZoneService.getExecutionZoneParameters(executionZone, stackDir)

        // get data from incoming json or xml
        request.withFormat {
            xml {
                NodeChild xml
                try {
                    xml = request.XML as NodeChild
                }
                catch (ConverterException e) {
                    this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, e.message)
                    hasError = Boolean.TRUE
                    return
                }

                origin_params.each { zoneparam ->
                    parameters[zoneparam.name] = xml.childNodes().findAll { it.name == 'parameter' }.findAll {
                        it.childNodes().findAll { it.text() == zoneparam.name }
                    }.collect { it.children[1].text() }
                }

            }
            json {
                String text = request.getReader().text
                def json

                try {
                   json = new JSONObject(text)
                }
                catch (JSONException e) {
                    this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, e.getMessage())
                    hasError = Boolean.TRUE
                    return
                }

                origin_params.each { zoneparam ->
                    parameters[zoneparam.name] = json.parameters.findAll{it.parameterName == zoneparam.name}.collect{it.parameterValue}
                }
            }
        }

        if (hasError) {
            return
        }

        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN) || userHasAccess(executionZone)) {

            if (parameters.any { key, value -> value.any {it == ''} || value == null}) {
                this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'No empty parameter values allowed - please check your data.')
                return
            }

            if(!SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN)) {
                // check if it allowed to change the parameters

                origin_params.each {
                    ProcessingParameter org_parameter = new ProcessingParameter(name: it.name, value: it.value.toString())

                    List<ProcessingParameter> testParamsList = []

                    if (parameters[it.name]) {
                        parameters[it.name].each { param ->
                            testParamsList.add(new ProcessingParameter(name: it.name, value: param))
                        }
                    }
                    else {
                        if (it.value.toString()) {
                            testParamsList.add(new ProcessingParameter(name: it.name, value: it.value.toString()))
                        }
                        else {
                            this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'No empty parameter values allowed - please check your data. Empty parameter: ' + it.name)
                            return
                        }

                    }

                    testParamsList.each { new_parameter ->
                        if (org_parameter.value != new_parameter.value && !executionZoneService.actionParameterEditAllowed(new_parameter, org_parameter)) {
                            //not allowed to change this param so change back
                            parameters[org_parameter.name][new_parameter.value] = org_parameter.value
                        }
                    }
                }
            }

            //get the name of all parameters which are not defined
            def names = origin_params.findAll {it.value == ''}.name
            int numberOfExecutions

            if (names.size() == 1) {
                numberOfExecutions = parameters[names[0]].size()

                if (numberOfExecutions == 1 && runs) {
                    numberOfExecutions = runs
                }
            }
            else if (names.size() == 0) {
                numberOfExecutions = runs
            }
            else {
                // get the number of parameters which are not fix
                def sizes = names.collect{it.size()}.unique()

                // only one exists - as a result the number of executions is the number of existing parameters or 'number' if the value is '1'
                if (sizes.size() == 1) {
                    if (sizes.first() == 1 && runs) {
                        numberOfExecutions = runs
                    }
                    else {
                        numberOfExecutions = sizes.first()
                    }
                }
                else if (sizes.size() == 2) {
                    //two different exists
                    if (sizes.any{it == 1}) {
                        // one or more parameter is 1 so it will be used as fix parameter for all executions, the number of existing other ones is the number of executions
                        numberOfExecutions = sizes.find { it != 1}
                    }
                    else {
                        //ERROR - cannot decide when to use the different parameters
                        this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'Number of parameters which have to be set by user are not equal.')
                        return
                    }
                }
                else {
                    //more than two exists - also cannot decide when to use the different parameters
                    this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'Number of parameters which have to be set by user are not equal.')
                    return
                }
            }

            if (numberOfExecutions != quantity) {
                this.renderRestResult(HttpStatus.BAD_REQUEST, null, null, 'The calculated number of executions does not match your expection. Calculated number of ' +
                        'Executions: ' + numberOfExecutions + '. Quantity: ' + quantity + '. Please check your data.')
                return
            }

            numberOfExecutions.times { int idx ->

                Map singleParams = [:]

                parameters.each { param ->
                    if (param.value.size() > idx) {
                        singleParams[param.key] = param.value[idx]
                    } else {
                        singleParams[param.key] = param.value.last()
                    }
                }

                // create action with zone, stackdir and parameters
                ExecutionZoneAction action = executionZoneService.createExecutionZoneAction(executionZone, stackDir, singleParams)
                //publish event to start execution
                applicationEventPublisher.publishEvent(new ProcessingEvent(action, springSecurityService.currentUser, "REST-call run"))
                URI referral = new URI(grailsLinkGenerator.link(absolute: true, controller: 'executionZoneAction', action: 'rest', params: [id: action.id]))
                referralsCol.add(referral)
            }

            withFormat {
                xml {
                    def builder = new StreamingMarkupBuilder()
                    builder.encoding = 'UTF-8'

                    String executedActions = builder.bind {
                        executedActions {
                            execId executionZone.id
                            execAction executionZoneAction
                            referrals {
                                referralsCol.each {
                                    referral it.path
                                }
                            }
                        }
                    }

                    def xml = XmlUtil.serialize(executedActions).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                    xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                    render contentType: "text/xml", xml
                }
                json {
                    def executedActions = [:]

                    executedActions.put('execId', executionZone.id)
                    executedActions.put('execAction', executionZoneAction)
                    executedActions.put('referrals', referralsCol.collect {it.path})
                    render executedActions as JSON
                }
            }
        }
        else {
            renderRestResult(HttpStatus.FORBIDDEN, null, null, 'This user has no permission to execute this execution Zone.')
        }
    }

    /**
     * The method returns the hosts of an execution zone. The result could be more specific if 'hostState' parameter is added to the request url e.g. ?hostState=completed to return all
     * hosts with the state completed. It is also possible to add multiple states. In this case call the url with ?hostState=completed,created .
     */
    def listhosts = {
        ExecutionZone executionZone

        if (params.execId) {
            if(ExecutionZone.findById(params.execId as Long)){
                executionZone = ExecutionZone.findById(params.execId as Long)
            }
            else {
                this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'ExecutionZone with id ${params.execId} not found.')
                return
            }
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'ExecutionZone id (execId) not set.')
            return
        }

        def hostsFromZone = []

        if (params.hostState) {
            def hostStates = []

            if (params.hostState.contains(',')){
                hostStates = params.hostState.split(',')
            }
            else {
                hostStates.add(params.hostState as String)
            }

            hostStates.each {
                String state = it as String
                state = state.toUpperCase()
                if (HostState.values().find { it.toString() == state }) {
                    HostState hostState = HostState.valueOf(state)
                    hostsFromZone.addAll(Host.findAllByExecZoneAndState(executionZone, hostState))
                } else {
                    this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'No hoststate found for state: ' + params.hostState)
                    return
                }
            }
        }
        else {
            hostsFromZone = Host.findAllByExecZone(executionZone)
        }

        withFormat {
            xml {
                def builder = new StreamingMarkupBuilder()
                builder.encoding = 'UTF-8'

                String foundHosts = builder.bind {
                    hosts {
                        execId executionZone.id
                        hostsFromZone.each { hostElement ->
                            host {
                                hostname hostElement.hostname.toString()
                                cname hostElement.cname
                                hoststate hostElement.state.toString()
                                ipadress hostElement.ipAddress
                                serviceUrls {
                                    hostElement.serviceUrls.each { singleurl ->
                                        serviceUrl singleurl.url
                                    }
                                }
                            }
                        }
                    }
                }

                def xml = XmlUtil.serialize(foundHosts).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                render contentType: "text/xml", xml
            }
            json {
                Map hosts = [:]
                hosts.put('execId', executionZone.id)
                List host = hostsFromZone.collect{[hostname: it.hostname.toString(), cname: it.cname, hoststate: it.state.toString(), ipadress: it.ipAddress, serviceUrls: [it.serviceUrls.collect{it.url}]]}
                hosts.put('hosts', host)
                render hosts as JSON
            }
        }
    }

    /**
     * The method returns a list of all existing states of a host.
     */
    def listhoststates = {
        def hostStates = HostState.findAll().collect{it.toString()}

        withFormat {
            xml {
                def builder = new StreamingMarkupBuilder()
                builder.encoding = 'UTF-8'

                String states = builder.bind {
                    hoststates {
                        hostStates.each {
                            hoststate it
                        }
                    }
                }

                def xml = XmlUtil.serialize(states).replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n')
                xml = xml.replaceAll('<([^/]+?)/>', '<$1></$1>')
                render contentType: "text/xml", xml
            }
            json {
                Map jsonhoststates = [:]
                jsonhoststates.put('hoststates', hostStates)
                render jsonhoststates as JSON
            }
        }
    }

    /**
     * Check if the user is already in the cache and has access to the requested execution zone.
     * @param executionZone - the execution zone which has to be checked for access.
     * @return true if the user has access otherwise false.
     */
    private Boolean userHasAccess(ExecutionZone executionZone) {
        Long currentUserId = springSecurityService.getCurrentUserId() as Long
        return accessService.accessCache[currentUserId] != null ?
                accessService.accessCache[currentUserId][executionZone.id] :
                accessService.userHasAccess(executionZone)
    }

    /**
     * Check if the script file exists. If not it renders NOT_FOUND with the error message that the script file does not exists.
      * @param scriptDir the script File object.
     * @return true if exists otherwise false.
     */
    private Boolean isValidScriptDir(File scriptDir) {
        if (scriptDir.exists()) {
            return Boolean.TRUE
        }
        else {
            renderRestResult(HttpStatus.NOT_FOUND, null, null, 'The script with path ' + scriptDir.getPath() + ' does not exists.')
        }
        return Boolean.FALSE
    }
}