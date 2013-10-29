package org.zenboot.portal

import java.nio.charset.Charset;
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream


import org.springframework.dao.DataIntegrityViolationException
import org.zenboot.portal.processing.*

class TemplateController {
    


    static allowedMethods = [save: "POST", update: "POST", delete: "POST", upload: "POST"]

    def index() {
        redirect(action: "list", params: params)
    }

    def list() {
        params.max = Math.min(params.max ? params.int('max') : 10, 100)
        [templateInstanceList: Template.list(params), templateInstanceTotal: Template.count()]
    }

    def create() {
        [templateInstance: new Template(params)]
    }

    def save() {
        def templateInstance = new Template(params)
        if (!templateInstance.save(flush:true)) {
            render(view: "create", model: [templateInstance: templateInstance])
            return
        }

		flash.message = message(code: 'default.created.message', args: [message(code: 'template.label', default: 'Template'), templateInstance.id])
        redirect(controller: "executionZone", action: "show", id: templateInstance.executionZone.id)
    }
    
    def ajaxGetTemplateParameters() {
        def templateInstance = Template.get(params.id)
        
        if (!templateInstance) {
            this.renderRestResult(404, null, null, "Not Template exists for this id")
            return
        }
        render (contentType:"text/json"){
            template name:templateInstance.name, 
                    url:createLink(action: 'ajaxGetVersion', id:templateInstance.getTemplateObject().id),
                    templateUrl:createLink(action: 'ajaxGetTemplate', id:templateInstance.getTemplateObject().id),
                    deleteTemplateUrl:createLink(action: 'delete', id:templateInstance.id),
                    versions: array{
                        templateInstance.templateVersions.each {
                            version(id:it.id, 
                            create:it.dateCreated, 
                            url:createLink(action: 'ajaxGetVersion', id:it.id),
                            user:it.user)
                        }
                    },
                    dateCreated:templateInstance.dateCreated, 
                    updateUrl:createLink(action:'update', id:templateInstance.id)
        }
        return
    }
    
    def ajaxGetVersion() {
        def templateInstance = TemplateVersion.get(params.id)
        
        if (!templateInstance) {
            this.renderRestResult(404, null, null, "Not Version exists for this id")
            return
        }
        
        render (contentType:"text/json"){
          version(id:templateInstance.id, 
              create:templateInstance.dateCreated, 
              url:createLink(action: 'ajaxGetTemplate', id:templateInstance.id), 
              commentUrl:createLink(action: 'ajaxGetComment', id:templateInstance.id)
          )
        }
        
        return
    }
    
    def ajaxGetTemplate() {
        def templateInstance = TemplateVersion.get(params.id)
        
        if (!templateInstance) {
            this.renderRestResult(404, null, null, "Not Template exists for this id")
            return
        }
        
        render(text: templateInstance.content)
        return
    }
    
    def ajaxGetComment() {
        def templateInstance = TemplateVersion.get(params.id)
        
        if (!templateInstance) {
            this.renderRestResult(404, null, null, "Not Template exists for this id")
            return
        }
        
        render(text: templateInstance.comment)
        return
    }

    def show() {
        def templateInstance = Template.get(params.id)
        if (!templateInstance) {
			flash.message = message(code: 'default.not.found.message', args: [message(code: 'template.label', default: 'Template'), params.id])
            redirect(action: "list")
            return
        }

        [templateInstance: templateInstance]
    }

    def edit() {
        def templateInstance = Template.get(params.id)
        if (!templateInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'template.label', default: 'Template'), params.id])
            redirect(action: "list")
            return
        }

        [templateInstance: templateInstance]
    }

    def update() {
        def templateInstance = Template.get(params.id)
        
        if (!templateInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'template.label', default: 'Template'), params.id])
            redirect(controller: "executionZone", action: "show", id: templateInstance.executionZone.id)
            return
        }

        if (params.version) {
            def version = params.version.toLong()
            if (templateInstance.version > version) {
                templateInstance.errors.rejectValue("version", "default.optimistic.locking.failure",
                          [message(code: 'template.label', default: 'Template')] as Object[],
                          "Another user has updated this Template while you were editing")
                render(view: "edit", model: [templateInstance: templateInstance])
                return
            }
        }
        
        templateInstance.version++ // Workaround to force a object save. 
        
        templateInstance.properties = params
        if (!templateInstance.save(flush: true)) {
            render(view: "edit", model: [templateInstance: templateInstance])
            return
        }
        
        
        
		flash.message = message(code: 'default.updated.message', args: [message(code: 'template.label', default: 'Template'), templateInstance.id])
        redirect(controller: "executionZone", action: "show", id: templateInstance.executionZone.id)
    }
    
    def upload() {
        def executionZoneInstance = ExecutionZone.get(params.execId)
        
        if (!executionZoneInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), params.executionZone.id])
            redirect(controller: "executionZone", action: "index")
            return
        }
        
        def f = request.getFile('importFile')
        if (f.empty) {
            flash.message = 'file cannot be empty'
            render(controller: "executionZone", action: "show", id: executionZoneInstance.id)
            return
        }
        
        try{
            new File(this.grailsApplication.config.zenboot.template.tempDir.toString()).deleteDir()
            new File(this.grailsApplication.config.zenboot.template.tempDir.toString() + "/tmp").mkdirs()
        } catch (all) {
            flash.message = "Can't create tmp dir."
            render(controller: "executionZone", action: "show", id: executionZoneInstance.id)
        }
        
        f.transferTo(new File(this.grailsApplication.config.zenboot.template.tempDir.toString() + "/import.zip"))
        
        def zipFile = new ZipFile(new File(this.grailsApplication.config.zenboot.template.tempDir.toString() + "/import.zip"))
        
        int files = 0
        int imported = 0
        zipFile.entries().each {
            files++
            if(!(it =~ /(\/\.)|(\/$)/)){
              def name = (it.name =~ /.*\//).replaceAll("")
              Template template = Template.findByName(name) 
              if(template && params.updateTemplates) {
                template.template = zipFile.getInputStream(it).text
                template.message = params.commitMessage
                template.version++ // Workaround to force a object save. 
              }else{
                template = new Template(name: name, template: zipFile.getInputStream(it).text, message: params.commitMessage, executionZone:executionZoneInstance)
              }
              
              if(template.save(flush:true)){
                  imported++
              }
            }
         }
        
        
        new File(this.grailsApplication.config.zenboot.template.tempDir.toString()).deleteDir()
        
        flash.message = message(code: 'template.imported', default: "{1} of {0} templates imported!", args: [files, imported])
        redirect(controller: "executionZone", action: "show", id: executionZoneInstance.id)
        return
    }
    
    def export() {
        def executionZoneInstance = ExecutionZone.get(params.execId)
        
        if (!executionZoneInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), params.execId])
            redirect(controller: "executionZone", action: "index")
            return
        }
        
        try{
            new File(this.grailsApplication.config.zenboot.template.tempDir.toString()).deleteDir()
            new File(this.grailsApplication.config.zenboot.template.tempDir.toString()).mkdirs()
        } catch (all) {
            flash.message = "Can't create tmp dir."
            render(controller: "executionZone", action: "show", id: executionZoneInstance.id)
        }
        
        
        def exportFile = new File(this.grailsApplication.config.zenboot.template.tempDir.toString() + "/export.zip")
        def zipFile = new ZipOutputStream(new FileOutputStream(exportFile))
        
        
        if(executionZoneInstance.templates){
            executionZoneInstance.templates.each {
                zipFile.putNextEntry(new ZipEntry(it.name))
                zipFile.write(it.template.getBytes(Charset.forName("UTF-8")))
                zipFile.closeEntry();
            }
        } else {
            flash.message = "There are no Templates in this zone"
            render(controller: "executionZone", action: "show", id: executionZoneInstance.id)
        }
        
        
        
        zipFile.close()
        
        
        
        response.setContentType("application/octet-stream")
        response.setHeader("Content-disposition", "attachment;filename=${exportFile.getName()}")
        
        response.outputStream << exportFile.newInputStream() // Performing a binary stream copy
        
        new File(this.grailsApplication.config.zenboot.template.tempDir.toString()).deleteDir()
        return
    }

    def delete() {
        def templateInstance = Template.get(params.id)
        if (!templateInstance) {
			flash.message = message(code: 'default.not.found.message', args: [message(code: 'template.label', default: 'Template'), params.id])
            redirect(controller: "executionZone", action: "index")
            return
        }

        try {
            templateInstance.delete(flush: true)
			flash.message = message(code: 'default.deleted.message', args: [message(code: 'template.label', default: 'Template'), params.id])
            redirect(controller: "executionZone", action: "show", id:templateInstance.executionZone.id)
        }
        catch (DataIntegrityViolationException e) {
			flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'template.label', default: 'Template'), params.id])
            redirect(controller: "executionZone", action: "show", id:templateInstance.executionZone.id)
        }
    }
}
