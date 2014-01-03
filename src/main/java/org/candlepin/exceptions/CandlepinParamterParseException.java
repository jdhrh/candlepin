/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.exceptions;


/**
 * FilterParamException
 */
public class CandlepinParamterParseException extends RuntimeException {

    private String paramName;
    private String expectedFormat;

    public CandlepinParamterParseException(String paramName, String expectedFormat) {
        // NOTE: Exception message will be generated by BadRequestExceptionMapper.
        //       See the mapper class for details.
        this.paramName = paramName;
        this.expectedFormat = expectedFormat;
    }

    public String getParamName() {
        return this.paramName;
    }

    public String getExpectedFormat() {
        return this.expectedFormat;
    }
}
