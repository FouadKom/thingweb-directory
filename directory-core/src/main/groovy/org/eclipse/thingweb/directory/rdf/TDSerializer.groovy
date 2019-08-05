/********************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 * 
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the W3C Software Notice and
 * Document License (2015-05-13) which is available at
 * https://www.w3.org/Consortium/Legal/2015/copyright-software-and-document.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR W3C-20150513
 ********************************************************************************/
package org.eclipse.thingweb.directory.rdf

import groovy.json.*
import groovy.util.logging.Log

import java.io.InputStream
import java.io.OutputStream

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.util.Models
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.thingweb.directory.Resource
import org.eclipse.thingweb.directory.ResourceMalformedException
import org.eclipse.thingweb.directory.ResourceSerializer
import org.eclipse.thingweb.directory.utils.TDTransform
import org.eclipse.thingweb.directory.vocabulary.TD

import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import com.github.jsonldjava.core.JsonLdUtils
import com.github.jsonldjava.utils.JsonUtils

/**
 * Serializer implementation for Thing Description documents,
 * interpreted as JSON-LD 1.1 objects with normative context.
 * <p>
 * For non-TD formats, parsing and serialization are delegated
 * to the {@link RDFSerializer} class.
 *
 * @see
 *   <a href="http://www.w3.org/TR/wot-thing-description">
 *     W3C Thing Description Model
 *   </a>
 *
 * @author Victor Charpenay
 * @creation 06.08.2018
 *
 */
@Log
@Singleton
class TDSerializer implements ResourceSerializer {
	
	public static final TD_FRAME = ['@context': TDTransform.TD_CONTEXT_URI, '@type': 'Thing']
	
	public static final TD_CONTENT_FORMAT = 'application/td+json'

	@Override
	Resource readContent(InputStream i, String cf) {
		if (cf == TD_CONTENT_FORMAT) {
			// TODO process TD as JSON-LD 1.1 (not supported by RDF4J yet)
		}

		RDFResource res = RDFSerializer.instance.readContent(i, cf)

		def t = res.content.find({ st ->
			st.predicate == TD.HAS_PROPERTY_AFFORDANCE ||
			st.predicate == TD.HAS_ACTION_AFFORDANCE ||
			st.predicate == TD.HAS_EVENT_AFFORDANCE
		}).subject
		
		if (!t) throw new TDMalformedException('No instance of td:Thing found in TD content')

		if (IRI.isInstance(t)) res.id = t.stringValue()
		
		return res
	}

	@Override
	void writeContent(Resource res, OutputStream o, String cf) {
		def buf = o
		
		if (cf == TD_CONTENT_FORMAT) {
			buf = new ByteArrayOutputStream()
			cf = RDFFormat.JSONLD.defaultMIMEType
		}
		
		RDFSerializer.instance.writeContent(res, buf, cf)
		
		if (buf.is(o)) return // stream closed, no more processing required
		
		def opts = new JsonLdOptions(
			compactArrays: true,
			useNativeTypes: true,
			pruneBlankNodeIdentifiers: true
		)
		
		def obj = new JsonSlurper().parse(buf.toByteArray())
		def isGraphObject = { it.'@graph' && it.'@id' }
		
		if (obj.any(isGraphObject)) {
			log.info('Serializing resource as aggregation of TDs (e.g. lookup result)...')
			
			obj = obj.findAll(isGraphObject).collectEntries({ item ->
				def id = item.'@id'
				
				def td = JsonLdProcessor.frame(item.'@graph', TD_FRAME, opts)
				td = new TDTransform(td).asJsonLd11()

				[(id): (td)]
			})
		} else {
			log.info('Serializing single TD resource...')
			
			obj = JsonLdProcessor.frame(obj, TD_FRAME, opts)
			obj = new TDTransform(obj).asJsonLd11()
		}
		
		o.write(JsonOutput.toJson(obj).bytes)
		o.close()
	}

}
