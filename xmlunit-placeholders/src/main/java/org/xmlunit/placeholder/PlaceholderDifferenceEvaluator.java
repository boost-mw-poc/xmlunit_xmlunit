/*
  This file is licensed to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package org.xmlunit.placeholder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import org.w3c.dom.Node;
import org.xmlunit.diff.Comparison;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.DifferenceEvaluator;
import org.xmlunit.util.Nodes;

/**
 * This class is used to add placeholder feature to XML comparison.
 *
 * <p><b>This class and the whole module are considered experimental
 * and any API may change between releases of XMLUnit.</b></p>
 *
 * <p>To use it, just add it with {@link
 * org.xmlunit.builder.DiffBuilder} like below</p>
 *
 * <pre>
 * Diff diff = DiffBuilder.compare(control).withTest(test).withDifferenceEvaluator(new PlaceholderDifferenceEvaluator()).build();
 * </pre>
 *
 * <p>Supported scenarios are demonstrated in the unit tests
 * (PlaceholderDifferenceEvaluatorTest).</p>
 *
 * <p>Default delimiters for placeholder are <code>${</code> and
 * <code>}</code>. Arguments to placeholders are by default enclosed
 * in {@code (} and {@code )} and separated by {@code ,} - whitespace
 * is significant, arguments are not quoted.</p>
 *
 * <p>To use custom delimiters (in regular expression), create
 * instance with the {@link #PlaceholderDifferenceEvaluator(String,
 * String)} or {@link #PlaceholderDifferenceEvaluator(String, String,
 * String, String, String)} constructors.</p>
 *
 * @since 2.6.0
 */
public class PlaceholderDifferenceEvaluator implements DifferenceEvaluator {
    /**
     * Pattern used to find the start of a placeholder.
     */
    public static final String PLACEHOLDER_DEFAULT_OPENING_DELIMITER_REGEX = Pattern.quote("${");
    /**
     * Pattern used to find the end of a placeholder.
     */
    public static final String PLACEHOLDER_DEFAULT_CLOSING_DELIMITER_REGEX = Pattern.quote("}");
    /**
     * Pattern used to find the start of an argument list.
     * @since 2.7.0
     */
    public static final String PLACEHOLDER_DEFAULT_ARGS_OPENING_DELIMITER_REGEX = Pattern.quote("(");
    /**
     * Pattern used to find then end of an argument list.
     * @since 2.7.0
     */
    public static final String PLACEHOLDER_DEFAULT_ARGS_CLOSING_DELIMITER_REGEX = Pattern.quote(")");
    /**
     * Pattern used to find an argument separator.
     * @since 2.7.0
     */
    public static final String PLACEHOLDER_DEFAULT_ARGS_SEPARATOR_REGEX = Pattern.quote(",");

    private static final String PLACEHOLDER_PREFIX_REGEX = Pattern.quote("xmlunit.");
    private static final Map<String, PlaceholderHandler> KNOWN_HANDLERS;
    private static final String[] NO_ARGS = new String[0];

    static {
        Map<String, PlaceholderHandler> m = new HashMap<String, PlaceholderHandler>();
        for (PlaceholderHandler h : ServiceLoader.load(PlaceholderHandler.class)) {
            m.put(h.getKeyword(), h);
        }
        KNOWN_HANDLERS = Collections.unmodifiableMap(m);
    }

    private final Pattern placeholderRegex;
    private final Pattern argsRegex;
    private final String argsSplitter;

    /**
     * Creates a PlaceholderDifferenceEvaluator with default
     * delimiters {@link #PLACEHOLDER_DEFAULT_OPENING_DELIMITER_REGEX}
     * and {@link #PLACEHOLDER_DEFAULT_CLOSING_DELIMITER_REGEX}.
     */
    public PlaceholderDifferenceEvaluator() {
        this(null, null);
    }

    /**
     * Creates a PlaceholderDifferenceEvaluator with custom delimiters.
     * @param placeholderOpeningDelimiterRegex regular expression for
     * the opening delimiter of placeholder, defaults to {@link
     * PlaceholderDifferenceEvaluator#PLACEHOLDER_DEFAULT_OPENING_DELIMITER_REGEX}
     * if the parameter is null or blank
     * @param placeholderClosingDelimiterRegex regular expression for
     * the closing delimiter of placeholder, defaults to {@link
     * PlaceholderDifferenceEvaluator#PLACEHOLDER_DEFAULT_CLOSING_DELIMITER_REGEX}
     * if the parameter is null or blank
     */
    public PlaceholderDifferenceEvaluator(final String placeholderOpeningDelimiterRegex,
                                          final String placeholderClosingDelimiterRegex) {
        this(placeholderOpeningDelimiterRegex, placeholderClosingDelimiterRegex, null, null, null);
    }

    /**
     * Creates a PlaceholderDifferenceEvaluator with custom delimiters.
     * @param placeholderOpeningDelimiterRegex regular expression for
     * the opening delimiter of placeholder, defaults to {@link
     * PlaceholderDifferenceEvaluator#PLACEHOLDER_DEFAULT_OPENING_DELIMITER_REGEX}
     * if the parameter is null or blank
     * @param placeholderClosingDelimiterRegex regular expression for
     * the closing delimiter of placeholder, defaults to {@link
     * PlaceholderDifferenceEvaluator#PLACEHOLDER_DEFAULT_CLOSING_DELIMITER_REGEX}
     * if the parameter is null or blank
     * @param placeholderArgsOpeningDelimiterRegex regular expression for
     * the opening delimiter of the placeholder's argument list, defaults to {@link
     * PlaceholderDifferenceEvaluator#PLACEHOLDER_DEFAULT_ARGS_OPENING_DELIMITER_REGEX}
     * if the parameter is null or blank
     * @param placeholderArgsClosingDelimiterRegex regular expression for
     * the closing delimiter of the placeholder's argument list, defaults to {@link
     * PlaceholderDifferenceEvaluator#PLACEHOLDER_DEFAULT_ARGS_CLOSING_DELIMITER_REGEX}
     * if the parameter is null or blank
     * @param placeholderArgsSeparatorRegex regular expression for the
     * delimiter between arguments inside of the placeholder's
     * argument list, defaults to {@link
     * PlaceholderDifferenceEvaluator#PLACEHOLDER_DEFAULT_ARGS_SEPARATOR_REGEX}
     * if the parameter is null or blank
     *
     * @since 2.7.0
     */
    public PlaceholderDifferenceEvaluator(String placeholderOpeningDelimiterRegex,
        String placeholderClosingDelimiterRegex,
        String placeholderArgsOpeningDelimiterRegex,
        String placeholderArgsClosingDelimiterRegex,
        String placeholderArgsSeparatorRegex) {
        if (placeholderOpeningDelimiterRegex == null
            || placeholderOpeningDelimiterRegex.trim().length() == 0) {
            placeholderOpeningDelimiterRegex = PLACEHOLDER_DEFAULT_OPENING_DELIMITER_REGEX;
        }
        if (placeholderClosingDelimiterRegex == null
            || placeholderClosingDelimiterRegex.trim().length() == 0) {
            placeholderClosingDelimiterRegex = PLACEHOLDER_DEFAULT_CLOSING_DELIMITER_REGEX;
        }
        if (placeholderArgsOpeningDelimiterRegex == null
            || placeholderArgsOpeningDelimiterRegex.trim().length() == 0) {
            placeholderArgsOpeningDelimiterRegex = PLACEHOLDER_DEFAULT_ARGS_OPENING_DELIMITER_REGEX;
        }
        if (placeholderArgsClosingDelimiterRegex == null
            || placeholderArgsClosingDelimiterRegex.trim().length() == 0) {
            placeholderArgsClosingDelimiterRegex = PLACEHOLDER_DEFAULT_ARGS_CLOSING_DELIMITER_REGEX;
        }
        if (placeholderArgsSeparatorRegex == null
            || placeholderArgsSeparatorRegex.trim().length() == 0) {
            placeholderArgsSeparatorRegex = PLACEHOLDER_DEFAULT_ARGS_SEPARATOR_REGEX;
        }

        placeholderRegex = Pattern.compile("(\\s*" + placeholderOpeningDelimiterRegex
            + "\\s*" + PLACEHOLDER_PREFIX_REGEX + "(.+)" + "\\s*"
            + placeholderClosingDelimiterRegex + "\\s*)");
        argsRegex = Pattern.compile("((.*)\\s*" + placeholderArgsOpeningDelimiterRegex
            + "(.+)"
            + "\\s*" + placeholderArgsClosingDelimiterRegex + "\\s*)");
        argsSplitter = placeholderArgsSeparatorRegex;
    }

    public ComparisonResult evaluate(Comparison comparison, ComparisonResult outcome) {
        if (outcome == ComparisonResult.EQUAL) {
            return outcome;
        }

        Comparison.Detail controlDetails = comparison.getControlDetails();
        Node controlTarget = controlDetails.getTarget();
        Comparison.Detail testDetails = comparison.getTestDetails();
        Node testTarget = testDetails.getTarget();

        // comparing textual content of elements
        if (comparison.getType() == ComparisonType.TEXT_VALUE) {
            return evaluateConsideringPlaceholders((String) controlDetails.getValue(),
                (String) testDetails.getValue(), outcome);

        // "test document has no text-like child node but control document has"
        } else if (isMissingTextNodeDifference(comparison)) {
            return evaluateMissingTextNodeConsideringPlaceholders(comparison, outcome);

        // may be comparing TEXT to CDATA
        } else if (isTextCDATAMismatch(comparison)) {
            return evaluateConsideringPlaceholders(controlTarget.getNodeValue(), testTarget.getNodeValue(), outcome);

        // comparing textual content of attributes
        } else if (comparison.getType() == ComparisonType.ATTR_VALUE
                   && controlDetails.getValue() instanceof String
                   && testDetails.getValue() instanceof String) {
            return evaluateConsideringPlaceholders((String) controlDetails.getValue(),
                (String) testDetails.getValue(), outcome);

        // comparing QName content of xsi:type attributes
        } else if (comparison.getType() == ComparisonType.ATTR_VALUE
                   && controlDetails.getValue() instanceof QName
                   && testDetails.getValue() instanceof QName) {
            return evaluateConsideringPlaceholders((QName) controlDetails.getValue(),
                (QName) testDetails.getValue(), outcome);

        // "test document has no attribute but control document has"
        } else if (isMissingAttributeDifference(comparison)) {
            return evaluateMissingAttributeConsideringPlaceholders(comparison, outcome);

        // default, don't apply any placeholders at all
        } else {
            return outcome;
        }
    }

    private boolean isMissingTextNodeDifference(Comparison comparison) {
        return controlHasOneTextChildAndTestHasNone(comparison)
            || cantFindControlTextChildInTest(comparison);
    }

    private boolean controlHasOneTextChildAndTestHasNone(Comparison comparison) {
        Comparison.Detail controlDetails = comparison.getControlDetails();
        Node controlTarget = controlDetails.getTarget();
        Comparison.Detail testDetails = comparison.getTestDetails();
        return comparison.getType() == ComparisonType.CHILD_NODELIST_LENGTH &&
            Integer.valueOf(1).equals(controlDetails.getValue()) &&
            Integer.valueOf(0).equals(testDetails.getValue()) &&
            isTextLikeNode(controlTarget.getFirstChild());
    }

    private boolean cantFindControlTextChildInTest(Comparison comparison) {
        Node controlTarget = comparison.getControlDetails().getTarget();
        return comparison.getType() == ComparisonType.CHILD_LOOKUP
            && controlTarget != null && isTextLikeNode(controlTarget);
    }

    private ComparisonResult evaluateMissingTextNodeConsideringPlaceholders(Comparison comparison, ComparisonResult outcome) {
        Node controlTarget = comparison.getControlDetails().getTarget();
        String value;
        if (controlHasOneTextChildAndTestHasNone(comparison)) {
            value = controlTarget.getFirstChild().getNodeValue();
        } else {
            value = controlTarget.getNodeValue();
        }
        return evaluateConsideringPlaceholders(value, null, outcome);
    }

    private boolean isTextCDATAMismatch(Comparison comparison) {
        return comparison.getType() == ComparisonType.NODE_TYPE
            && isTextLikeNode(comparison.getControlDetails().getTarget())
            && isTextLikeNode(comparison.getTestDetails().getTarget());
    }

    private boolean isTextLikeNode(Node node) {
        short nodeType = node.getNodeType();
        return nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE;
    }

    private boolean isMissingAttributeDifference(Comparison comparison) {
        return comparison.getType() == ComparisonType.ELEMENT_NUM_ATTRIBUTES
            || (comparison.getType() == ComparisonType.ATTR_NAME_LOOKUP
                && comparison.getControlDetails().getTarget() != null
                && comparison.getControlDetails().getValue() != null);
    }

    private ComparisonResult evaluateMissingAttributeConsideringPlaceholders(Comparison comparison, ComparisonResult outcome) {
        if (comparison.getType() == ComparisonType.ELEMENT_NUM_ATTRIBUTES) {
            return evaluateAttributeListLengthConsideringPlaceholders(comparison, outcome);
        }
        String controlAttrValue = Nodes.getAttributes(comparison.getControlDetails().getTarget())
            .get((QName) comparison.getControlDetails().getValue());
        return evaluateConsideringPlaceholders(controlAttrValue, null, outcome);
    }

    private ComparisonResult evaluateAttributeListLengthConsideringPlaceholders(Comparison comparison,
        ComparisonResult outcome) {
        Map<QName, String> controlAttrs = Nodes.getAttributes(comparison.getControlDetails().getTarget());
        Map<QName, String> testAttrs = Nodes.getAttributes(comparison.getTestDetails().getTarget());

        int cAttrsMatched = 0;
        for (Map.Entry<QName, String> cAttr : controlAttrs.entrySet()) {
            String testValue = testAttrs.get(cAttr.getKey());
            if (testValue == null) {
                ComparisonResult o = evaluateConsideringPlaceholders(cAttr.getValue(), null, outcome);
                if (o != ComparisonResult.EQUAL) {
                    return outcome;
                }
            } else {
                cAttrsMatched++;
            }
        }
        if (cAttrsMatched != testAttrs.size()) {
            // there are unmatched test attributes
            return outcome;
        }
        return ComparisonResult.EQUAL;
    }

    private ComparisonResult evaluateConsideringPlaceholders(QName controlQName, QName testQName,
        ComparisonResult outcome) {
        if (controlQName.getNamespaceURI().equals(testQName.getNamespaceURI())) {
            return evaluateConsideringPlaceholders(controlQName.getLocalPart(), testQName.getLocalPart(),
                                                   outcome);
        }

        return outcome;
    }

    private ComparisonResult evaluateConsideringPlaceholders(String controlText, String testText,
        ComparisonResult outcome) {
        final Matcher placeholderMatcher = placeholderRegex.matcher(controlText);
        if (placeholderMatcher.find()) {
            final String content = placeholderMatcher.group(2).trim();
            final Matcher argsMatcher = argsRegex.matcher(content);
            final String keyword;
            final String[] args;
            if (argsMatcher.find()) {
                keyword = argsMatcher.group(2).trim();
                args = argsMatcher.group(3).split(argsSplitter);
            } else {
                keyword = content;
                args = NO_ARGS;
            }
            if (isKnown(keyword)) {
                if (!placeholderMatcher.group(1).trim().equals(controlText.trim())) {
                    throw new RuntimeException("The placeholder must exclusively occupy the text node.");
                }
                return evaluate(keyword, testText, args);
            }
        }

        // no placeholder at all or unknown keyword
        return outcome;
    }

    private boolean isKnown(final String keyword) {
        return KNOWN_HANDLERS.containsKey(keyword);
    }

    private ComparisonResult evaluate(final String keyword, final String testText, final String[] args) {
        return KNOWN_HANDLERS.get(keyword).evaluate(testText, args);
    }
}
