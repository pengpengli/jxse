/*
 * Copyright (c) 2001-2007 Sun Microsystems, Inc.  All rights reserved.
 *
 *  The Sun Project JXTA(TM) Software License
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, 
 *     this list of conditions and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution.
 *
 *  3. The end-user documentation included with the redistribution, if any, must 
 *     include the following acknowledgment: "This product includes software 
 *     developed by Sun Microsystems, Inc. for JXTA(TM) technology." 
 *     Alternately, this acknowledgment may appear in the software itself, if 
 *     and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must 
 *     not be used to endorse or promote products derived from this software 
 *     without prior written permission. For written permission, please contact 
 *     Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA", nor may 
 *     "JXTA" appear in their name, without prior written permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 *  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND 
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL SUN 
 *  MICROSYSTEMS OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  JXTA is a registered trademark of Sun Microsystems, Inc. in the United 
 *  States and other countries.
 *
 *  Please see the license information page at :
 *  <http://www.jxta.org/project/www/license.html> for instructions on use of 
 *  the license in source files.
 *
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many individuals 
 *  on behalf of Project JXTA. For more information on Project JXTA, please see 
 *  http://www.jxta.org.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation. 
 */

package net.jxta.id.jxta;

import net.jxta.id.IDFactory;

/**
 * The 'jxta' ID Format is used for the presentation of a limited number of
 * well-known ids. These are the null id, the world peer group id, and the
 * default net peer group id. JXTA depends on being able to refer to these
 * standard entities in a common way regardless of what other id formats are
 * used, indeed this ID Format and these ids exist so that there are not a
 * different representations for the ids of these entities with each ID Format.
 *
 * <p/>Rather than return its own version of these well known IDs, each ID
 * Format <b>MUST</b> return these IDs as appropriate.
 *
 * <ul>
 * <li>the null id - the NullID is often used as a placeholder in fields which
 * are uninitialized.</li>
 *
 * <li>the world peer group id - the id of the world peer group.</li>
 *
 * <li>the default net peer group id - the id of the default net peer group.</li>
 * </ul>
 *
 * @see net.jxta.id.ID
 * @see net.jxta.id.IDFactory
 @see <a href="https://jxta-spec.dev.java.net/nonav/JXTAProtocols.html#ids" target='_blank'>JXTA Protocols Specification : IDs</a>
 @see <a href="https://jxta-spec.dev.java.net/nonav/JXTAProtocols.html#ids-jinf" target='_blank'>JXTA Protocols Specification : ?jxta? ID Format</a>
 *
 **/
public final class IDFormat {

    /**
     * The name associated with this ID Format.
     */ 
    final static String JXTAFormat = "jxta";

    /**
     * The instantiator for this ID Format which is used by the IDFactory.
     *
     * @since JXTA 1.0
     **/ 
    public static final IDFactory.Instantiator INSTANTIATOR = new Instantiator();

    /**
     *  Private Constructor. This class cannot be instantiated.
     **/
    private IDFormat() {}
}
