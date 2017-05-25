/*
 * Academic Free License ("AFL") v. 3.0
 * Copyright 2014-2017 Owen Rubel
 *
 * IO State (tm) Owen Rubel 2014
 * API Chaining (tm) Owen Rubel 2013
 *
 *   https://opensource.org/licenses/AFL-3.0
 */

package net.nosegrind.apiframework

import grails.converters.JSON
//import grails.converters.XML
import grails.plugin.cache.CachePut
import grails.plugin.cache.GrailsCacheManager
import org.grails.groovy.grails.commons.*
import grails.core.GrailsApplication
/*
* Want to be able to :
*  - cache each 'class/method' and associated start/end times and order  in which they are called
*  - prior to API response return, generateJSON() and return JSON as response
 */

class TraceCacheService{

	static transactional = false
	
	GrailsApplication grailsApplication
	GrailsCacheManager grailsCacheManager
	
	// called through generateJSON()

	public void flushCache(String uri){
		try{
			grailsCacheManager?.getCache('Trace').clear()
			//Cache cache = grailsCacheManager.getCache('Trace')
			//cache.clear()
		}catch(Exception e){
			throw new Exception("[TraceCacheService :: getTraceCache] : Exception - full stack trace follows:",e)
		}
	}

	@CachePut(value="Trace",key="#uri")
	LinkedHashMap putTraceCache(String uri, LinkedHashMap cache){
		try{
			return cache
		}catch(Exception e){
			throw new Exception("[TraceCacheService :: putTraceCache] : Exception - full stack trace follows:",e)
		}
	}

	@CachePut(value="Trace",key="#uri")
	LinkedHashMap setTraceMethod(String uri,LinkedHashMap cache){
		try{
			return cache
		}catch(Exception e){
			throw new Exception("[TraceCacheService :: setTraceCache] : Exception - full stack trace follows:",e)
		}
	}

	LinkedHashMap getTraceCache(String uri){
		try{
			def temp = grailsCacheManager?.getCache('Trace')
			def cache = temp?.get(uri)
			if(cache?.get()){
				return cache.get() as LinkedHashMap
			}else{ 
				return [:] 
			}

		}catch(Exception e){
			throw new Exception("[TraceCacheService :: getTraceCache] : Exception - full stack trace follows:",e)
		}
	}
	
	List getCacheNames(){
		List cacheNames = []
		cacheNames = grailsCacheManager?.getCache('Trace')?.getAllKeys() as List
		return cacheNames
	}
}