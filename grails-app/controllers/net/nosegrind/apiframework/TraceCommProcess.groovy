/*
 * The MIT License (MIT)
 * Copyright 2014 Owen Rubel
 *
 * IO State (tm) Owen Rubel 2014
 * API Chaining (tm) Owen Rubel 2013
 *
 *   https://opensource.org/licenses/MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright/trademark notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nosegrind.apiframework

import grails.converters.JSON
import grails.converters.XML

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import java.text.SimpleDateFormat

import static groovyx.gpars.GParsPool.withPool
import grails.converters.JSON
import grails.converters.XML
import grails.web.servlet.mvc.GrailsParameterMap

import javax.servlet.forward.*
import org.grails.groovy.grails.commons.*
import grails.core.GrailsApplication
import grails.util.Holders

import org.grails.core.DefaultGrailsDomainClass


//import groovy.transform.CompileStatic
//import grails.compiler.GrailsCompileStatic
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler


// extended by TraceCommLayer
abstract class TraceCommProcess{

    @Resource
    GrailsApplication grailsApplication

    ApiCacheService apiCacheService
    @Resource
    TraceService traceService

    List formats = ['text/html','text/json','application/json','text/xml','application/xml']
    List optionalParams = ['method','format','contentType','encoding','action','controller','v','apiCombine', 'apiObject','entryPoint','uri']

    boolean batchEnabled = Holders.grailsApplication.config.apitoolkit.batching.enabled
    boolean chainEnabled = Holders.grailsApplication.config.apitoolkit.chaining.enabled


/*
    void setChainParams(GrailsParameterMap params){
        if (chainEnabled) {
            params.apiChain = content?.chain
        }
    }

    LinkedHashMap getApiObjectParams(LinkedHashMap definitions){
        try{
            LinkedHashMap apiList = [:]
            definitions.each{ key,val ->
                if(request.isUserInRole(key) || key=='permitAll'){
                    val.each{ it ->
                        if(it){
                            apiList[it.name] = it.paramType
                        }
                    }
                }
            }
            return apiList
        }catch(Exception e){
            throw new Exception("[ParamsService :: getApiObjectParams] : Exception - full stack trace follows:",e)
        }
        return [:]
    }
*/

    public List getApiParams(LinkedHashMap definitions){
        try{
            traceService.startTrace('TraceCommProcess','getApiParams')
            List apiList = []
            definitions.each(){ key, val ->
                if (request.isUserInRole(key) || key == 'permitAll') {
                    val.each(){ it2 ->
                        apiList.add(it2.name)
                    }
                }
            }
            traceService.endTrace('TraceCommProcess','getApiParams')
            return apiList
        }catch(Exception e){
            throw new Exception("[ParamsService :: getApiParams] : Exception - full stack trace follows:",e)
        }
    }


    boolean checkDeprecationDate(String deprecationDate){
        try{
            traceService.startTrace('TraceCommProcess','checkDeprecationDate')
            def ddate = new SimpleDateFormat("MM/dd/yyyy").parse(deprecationDate)
            def deprecated = new Date(ddate.time)
            def today = new Date()
            if(deprecated < today ) {
                traceService.endTrace('TraceCommProcess','checkDeprecationDate')
                return true
            }
            traceService.endTrace('TraceCommProcess','checkDeprecationDate')
            return false
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: checkDeprecationDate] : Exception - full stack trace follows:",e)
        }
    }

    boolean checkRequestMethod(String method, boolean restAlt){
        traceService.startTrace('TraceCommProcess','checkRequestMethod')
        if(!restAlt) {
            traceService.endTrace('TraceCommProcess','checkRequestMethod')
            return (method == request.method.toUpperCase()) ? true : false
        }
        traceService.endTrace('TraceCommProcess','checkRequestMethod')
        return true
    }

    // TODO: put in OPTIONAL toggle in application.yml to allow for this check
    boolean checkURIDefinitions(GrailsParameterMap params,LinkedHashMap requestDefinitions){
        traceService.startTrace('TraceCommProcess','checkURIDefinitions')
        List reservedNames = ['batchLength','batchInc']
        try{
            String authority = getUserRole() as String
            List temp = (requestDefinitions["${authority}"])?requestDefinitions["${authority}"] as List:requestDefinitions['permitAll'] as List
            List requestList = temp.collect(){ it.name }

            Map methodParams = getMethodParams(params)

            List paramsList = methodParams.keySet() as List

            // remove reservedNames from List
            reservedNames.each(){ paramsList.remove(it) }

            if (paramsList.size() == requestList.intersect(paramsList).size()) {
                traceService.endTrace('TraceCommProcess','checkURIDefinitions')
                return true
            }
            traceService.endTrace('TraceCommProcess','checkURIDefinitions')
            return false
        }catch(Exception e) {
           throw new Exception("[ApiCommProcess :: checkURIDefinitions] : Exception - full stack trace follows:",e)
        }
        return false
    }

    LinkedHashMap parseResponseMethod(HttpServletRequest request, GrailsParameterMap params, LinkedHashMap result){
        traceService.startTrace('TraceCommProcess','parseResponseMethod')
        LinkedHashMap data = [:]
        String defaultEncoding = Holders.grailsApplication.config.apitoolkit.encoding
        String encoding = request.getHeader('accept-encoding')?request.getHeader('accept-encoding'):defaultEncoding
        switch(request.method) {
            case 'PURGE':
                // cleans cache; disabled for now
                break;
            case 'TRACE':
                break;
            case 'HEAD':
                break;
            case 'OPTIONS':
                String doc = getApiDoc(params)
                data = ['content':doc,'contentType':request.getAttribute('contentType'),'encoding':encoding]
                break;
            case 'GET':
            case 'PUT':
            case 'POST':
            case 'DELETE':
                String content
                switch(request.format.toUpperCase()){
                    case 'XML':
                        content = result as XML
                        break
                    case 'JSON':
                    default:
                        content = result as JSON
                        data = ['content':content,'contentType':request.getAttribute('contentType'),'encoding':encoding]
                }
                break;
        }
        traceService.endTrace('TraceCommProcess','parseResponseMethod')
        return ['apiToolkitContent':data.content,'apiToolkitType':request.getAttribute('contentType'),'apiToolkitEncoding':encoding]
    }

    LinkedHashMap parseRequestMethod(HttpServletRequest request, GrailsParameterMap params){
        traceService.startTrace('TraceCommProcess','parseRequestMethod')
        LinkedHashMap data = [:]
        String defaultEncoding = grailsApplication.config.apitoolkit.encoding
        String encoding = request.getHeader('accept-encoding')?request.getHeader('accept-encoding'):defaultEncoding
        switch(request.method) {
            case 'PURGE':
                // cleans cache; disabled for now
                break;
            case 'TRACE':
                // placeholder
                break;
            case 'HEAD':
                // placeholder
                break;
            case 'OPTIONS':
                String doc = getApiDoc(params)
                data = ['content':doc,'contentType':request.getAttribute('contentType'),'encoding':encoding]
                break;
        }

        traceService.endTrace('TraceCommProcess','parseRequestMethod')
        return ['apiToolkitContent':data.content,'apiToolkitType':request.getAttribute('contentType'),'apiToolkitEncoding':encoding]
    }

    LinkedHashMap parseURIDefinitions(LinkedHashMap model,List responseList){
        try{
            traceService.startTrace('TraceCommProcess','parseURIDefinitions')
            String msg = 'Error. Invalid variables being returned. Please see your administrator'

            List paramsList
            Integer msize = model.size()
            switch(msize) {
                case 0:
                    return [:]
                    break;
                case 1:
                    paramsList = (model.keySet()!=['id'])?model.entrySet().iterator().next() as List : model.keySet() as List
                    break;
                default:
                    paramsList = model.keySet() as List
                    break;
            }

            paramsList?.removeAll(optionalParams)

            if(!responseList.containsAll(paramsList)){

                paramsList.removeAll(responseList)
                paramsList.each() { it2 ->
                    model.remove("${it2}".toString())
                }

                if(!paramsList){
                    traceService.endTrace('TraceCommProcess','parseURIDefinitions')
                    return [:]
                }else{
                    traceService.endTrace('TraceCommProcess','parseURIDefinitions')
                    return model
                }
            }else{
                traceService.endTrace('TraceCommProcess','parseURIDefinitions')
                return model
            }

        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: parseURIDefinitions] : Exception - full stack trace follows:",e)
        }
    }

    boolean isRequestMatch(String protocol,String method){
        traceService.startTrace('TraceCommProcess','isRequestMatch')
        if(['TRACERT','OPTIONS','HEAD'].contains(method)){
            traceService.endTrace('TraceCommProcess','isRequestMatch')
            return true
        }else{
            if(protocol == method){
                traceService.endTrace('TraceCommProcess','isRequestMatch')
                return true
            }else{
                traceService.endTrace('TraceCommProcess','isRequestMatch')
                return false
            }
        }
        return false
    }

    /*
    List getRedirectParams(GrailsParameterMap params){
        def uri = grailsApplication.mainContext.servletContext.getControllerActionUri(request)
        return uri[1..(uri.size()-1)].split('/')
    }
    */

    Map getMethodParams(GrailsParameterMap params){
        try{
            traceService.startTrace('TraceCommProcess','getMethodParams')
            Map paramsRequest = [:]
            List myList = [1,2,3,4];
            paramsRequest = params.findAll { it2 -> !optionalParams.contains(it2.key) }
            traceService.endTrace('TraceCommProcess','getMethodParams')
            return paramsRequest
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: getMethodParams] : Exception - full stack trace follows:",e)
        }
        return [:]
    }

    String getUserRole() {
        String authority = 'permitAll'
        if (springSecurityService.loggedIn){
            authority = springSecurityService.principal.authorities*.authority[0] as String
        }
        return authority
    }

    Boolean apiRoles(List list) {
        traceService.startTrace('TraceCommProcess','apiRoles')
        if(springSecurityService.principal.authorities*.authority.any { list.contains(it) }){
            return true
        }
        traceService.endTrace('TraceCommProcess','apiRoles')
        return false
    }


    String getApiDoc(GrailsParameterMap params){
        // TODO: Need to compare multiple authorities
        // TODO: check for ['doc'][role] in cache; if none, continue

        LinkedHashMap newDoc = [:]
        List paramDescProps = ['paramType','idReferences','name','description']
        try{
            def controller = grailsApplication.getArtefactByLogicalPropertyName('Controller', params.controller)
            if(controller){
                def cache = (params.controller)?apiCacheService.getApiCache(params.controller):null
                if(cache){
                    if(cache[params.apiObject][params.action]){

                        def doc = cache[params.apiObject][params.action].doc
                        def path = doc?.path
                        def method = doc?.method
                        def description = doc?.description


                        //def authority = springSecurityService.principal.authorities*.authority[0]
                        newDoc[params.action] = ['path':path,'method':method,'description':description]
                        if(doc.receives){
                            newDoc[params.action].receives = []

                            doc.receives.each{ it ->
                                if(apiRoles([it.key]) || it.key=='permitAll'){
                                    it.value.each(){ it2 ->
                                        LinkedHashMap values = [:]
                                        it2.each(){ it3 ->
                                            if(paramDescProps.contains(it3.key)){
                                                values[it3.key] = it3.value
                                            }
                                        }
                                        if(values) {
                                            newDoc[params.action].receives.add(values)
                                        }
                                    }

                                }
                            }
                        }

                        if(doc.returns){
                            newDoc[params.action].returns = []
                            List jsonReturns = []
                            doc.returns.each(){ v ->
                                if(apiRoles([v.key]) || v.key=='permitAll'){
                                    jsonReturns.add(["${v.key}":v.value])
                                    v.value.each(){ v2 ->
                                        LinkedHashMap values3 = [:]
                                        v2.each(){ v3 ->
                                            if(paramDescProps.contains(v3.key)){
                                                values3[v3.key] = v3.value
                                            }
                                        }
                                        if(values3) {
                                            newDoc[params.action].returns.add(values3)
                                        }
                                    }
                                    //newDoc[params.action].returns[v.key] = v.value
                                }
                            }

                            //newDoc[params.action].json = processJson(newDoc[params.action].returns)

                            newDoc[params.action].json = processJson(jsonReturns[0] as LinkedHashMap)
                        }

                        if(doc.errorcodes){
                            doc.errorcodes.each{ it ->
                                newDoc[params.action].errorcodes.add(it)
                            }
                        }

                        // store ['doc'][role] in cache

                        return newDoc as JSON
                    }
                }
            }
            return [:]
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: getApiDoc] : Exception - full stack trace follows:",e)
        }
    }

    // Used by getApiDoc
    private String processJson(LinkedHashMap returns){

        // TODO: Need to compare multiple authorities
        try{
            traceService.startTrace('TraceCommProcess','processJson')
            LinkedHashMap json = [:]
            returns.each{ p ->
                p.value.each{ it ->
                    if(it) {
                        ParamsDescriptor paramDesc = it

                        LinkedHashMap j = [:]
                        if (paramDesc?.values) {
                            j["$paramDesc.name"] = []
                        } else {
                            String dataName = (['PKEY', 'FKEY', 'INDEX'].contains(paramDesc?.paramType?.toString())) ? 'ID' : paramDesc.paramType
                            j = (paramDesc?.mockData?.trim()) ? ["$paramDesc.name": "$paramDesc.mockData"] : ["$paramDesc.name": "$dataName"]
                        }
                        withPool(20) { pool ->
                            j.eachParallel { key, val ->
                                if (val instanceof List) {
                                    def child = [:]
                                    withExistingPool(pool, {
                                        val.eachParallel { it2 ->
                                            withExistingPool(pool, {
                                                it2.eachParallel { key2, val2 ->
                                                    child[key2] = val2
                                                }
                                            })
                                        }
                                    })
                                    json[key] = child
                                } else {
                                    json[key] = val
                                }
                            }
                        }
                    }
                }
            }

            String jsonReturn
            if(json){
                jsonReturn = json as JSON
            }
            traceService.endTrace('TraceCommProcess','processJson')
            return jsonReturn
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: processJson] : Exception - full stack trace follows:",e)
        }
    }

    LinkedHashMap convertModel(Map map){
        try{
            traceService.startTrace('TraceCommProcess','convertModel')
            LinkedHashMap newMap = [:]
            String k = map.entrySet().toList().first().key
            if(map && (!map?.response && !map?.metaClass && !map?.params)){
                if (DomainClassArtefactHandler.isDomainClass(map[k].getClass())) {
                    newMap = formatDomainObject(map[k])
                    traceService.endTrace('TraceCommProcess','convertModel')
                    return newMap
                } else if (['class java.util.LinkedList', 'class java.util.ArrayList'].contains(map[k].getClass())) {
                    newMap = formatList(map[k])
                    traceService.endTrace('TraceCommProcess','convertModel')
                    return newMap
                } else if (['class java.util.Map', 'class java.util.LinkedHashMap'].contains(map[k].getClass())) {
                    newMap = formatMap(map[k])
                    traceService.endTrace('TraceCommProcess','convertModel')
                    return newMap
                }
            }
            traceService.endTrace('TraceCommProcess','convertModel')
            return newMap
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: convertModel] : Exception - full stack trace follows:",e)
        }
    }

    // PostProcessService
    LinkedHashMap formatDomainObject(Object data){
        try{
            traceService.startTrace('TraceCommProcess','formatDomainObject')
            LinkedHashMap newMap = [:]

            newMap.put('id',data?.id)
            newMap.put('version',data?.version)

            DefaultGrailsDomainClass d = new DefaultGrailsDomainClass(data.class)
            d.persistentProperties.each() { it ->
                newMap[it.name] = (DomainClassArtefactHandler.isDomainClass(data[it.name].getClass())) ? data."${it.name}".id : data[it.name]
            }
            traceService.endTrace('TraceCommProcess','formatDomainObject')
            return newMap
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: formatDomainObject] : Exception - full stack trace follows:",e)
        }
    }

    // PostProcessService
    LinkedHashMap formatList(List list){
        traceService.startTrace('TraceCommProcess','formatList')
        LinkedHashMap newMap = [:]
        list.eachWithIndex(){ val, key ->
            if(val){
                if(DomainClassArtefactHandler.isDomainClass(val.getClass())){
                    newMap[key]=formatDomainObject(val)
                }else{
                    newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map)?val:val.toString()
                }
            }
        }
        traceService.endTrace('TraceCommProcess','formatList')
        return newMap
    }


}