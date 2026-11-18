package com.personal.rebooked.author.importer;

import java.util.Map;
import java.util.Set;

/**
 * Wikidata country items (P27, country of citizenship) to ISO 3166-1 alpha-2 codes. Covers every African country,
 * plus the countries most authors on Rebooked are likely to come from. Ids checked against Wikidata labels.
 */
public final class WikidataCountries {
    private WikidataCountries() {}

    public static final Map<String, String> AFRICA = Map.ofEntries(
            Map.entry("Q262", "DZ"), // Algeria
            Map.entry("Q916", "AO"), // Angola
            Map.entry("Q962", "BJ"), // Benin
            Map.entry("Q963", "BW"), // Botswana
            Map.entry("Q965", "BF"), // Burkina Faso
            Map.entry("Q967", "BI"), // Burundi
            Map.entry("Q1009", "CM"), // Cameroon
            Map.entry("Q1011", "CV"), // Cape Verde
            Map.entry("Q929", "CF"), // Central African Republic
            Map.entry("Q657", "TD"), // Chad
            Map.entry("Q970", "KM"), // Comoros
            Map.entry("Q974", "CD"), // Democratic Republic of the Congo
            Map.entry("Q971", "CG"), // Republic of the Congo
            Map.entry("Q977", "DJ"), // Djibouti
            Map.entry("Q79", "EG"), // Egypt
            Map.entry("Q983", "GQ"), // Equatorial Guinea
            Map.entry("Q986", "ER"), // Eritrea
            Map.entry("Q1050", "SZ"), // Eswatini
            Map.entry("Q115", "ET"), // Ethiopia
            Map.entry("Q1000", "GA"), // Gabon
            Map.entry("Q1005", "GM"), // The Gambia
            Map.entry("Q117", "GH"), // Ghana
            Map.entry("Q1006", "GN"), // Guinea
            Map.entry("Q1007", "GW"), // Guinea-Bissau
            Map.entry("Q1008", "CI"), // Ivory Coast
            Map.entry("Q114", "KE"), // Kenya
            Map.entry("Q1013", "LS"), // Lesotho
            Map.entry("Q1014", "LR"), // Liberia
            Map.entry("Q1016", "LY"), // Libya
            Map.entry("Q1019", "MG"), // Madagascar
            Map.entry("Q1020", "MW"), // Malawi
            Map.entry("Q912", "ML"), // Mali
            Map.entry("Q1025", "MR"), // Mauritania
            Map.entry("Q1027", "MU"), // Mauritius
            Map.entry("Q1028", "MA"), // Morocco
            Map.entry("Q1029", "MZ"), // Mozambique
            Map.entry("Q1030", "NA"), // Namibia
            Map.entry("Q1032", "NE"), // Niger
            Map.entry("Q1033", "NG"), // Nigeria
            Map.entry("Q1037", "RW"), // Rwanda
            Map.entry("Q1039", "ST"), // Sao Tome and Principe
            Map.entry("Q1041", "SN"), // Senegal
            Map.entry("Q1042", "SC"), // Seychelles
            Map.entry("Q1044", "SL"), // Sierra Leone
            Map.entry("Q1045", "SO"), // Somalia
            Map.entry("Q258", "ZA"), // South Africa
            Map.entry("Q958", "SS"), // South Sudan
            Map.entry("Q1049", "SD"), // Sudan
            Map.entry("Q924", "TZ"), // Tanzania
            Map.entry("Q945", "TG"), // Togo
            Map.entry("Q948", "TN"), // Tunisia
            Map.entry("Q1036", "UG"), // Uganda
            Map.entry("Q953", "ZM"), // Zambia
            Map.entry("Q954", "ZW"), // Zimbabwe
            Map.entry("Q6250", "EH") // Western Sahara
    );

    public static final Map<String, String> OTHERS = Map.ofEntries(
            Map.entry("Q145", "GB"), // United Kingdom
            Map.entry("Q30", "US"), // United States
            Map.entry("Q16", "CA"), // Canada
            Map.entry("Q142", "FR"), // France
            Map.entry("Q183", "DE"), // Germany
            Map.entry("Q668", "IN"), // India
            Map.entry("Q27", "IE"), // Ireland
            Map.entry("Q408", "AU"), // Australia
            Map.entry("Q664", "NZ"), // New Zealand
            Map.entry("Q766", "JM"), // Jamaica
            Map.entry("Q754", "TT"), // Trinidad and Tobago
            Map.entry("Q155", "BR"), // Brazil
            Map.entry("Q148", "CN"), // China
            Map.entry("Q17", "JP"), // Japan
            Map.entry("Q159", "RU"), // Russia
            Map.entry("Q38", "IT"), // Italy
            Map.entry("Q29", "ES"), // Spain
            Map.entry("Q45", "PT"), // Portugal
            Map.entry("Q55", "NL"), // Netherlands
            Map.entry("Q34", "SE"), // Sweden
            Map.entry("Q31", "BE"), // Belgium
            Map.entry("Q39", "CH"), // Switzerland
            Map.entry("Q20", "NO"), // Norway
            Map.entry("Q35", "DK"), // Denmark
            Map.entry("Q33", "FI"), // Finland
            Map.entry("Q36", "PL"), // Poland
            Map.entry("Q41", "GR"), // Greece
            Map.entry("Q43", "TR"), // Turkey
            Map.entry("Q96", "MX"), // Mexico
            Map.entry("Q414", "AR"), // Argentina
            Map.entry("Q298", "CL"), // Chile
            Map.entry("Q739", "CO"), // Colombia
            Map.entry("Q241", "CU"), // Cuba
            Map.entry("Q790", "HT"), // Haiti
            Map.entry("Q244", "BB"), // Barbados
            Map.entry("Q734", "GY"), // Guyana
            Map.entry("Q843", "PK"), // Pakistan
            Map.entry("Q902", "BD"), // Bangladesh
            Map.entry("Q854", "LK"), // Sri Lanka
            Map.entry("Q884", "KR"), // South Korea
            Map.entry("Q865", "TW"), // Taiwan
            Map.entry("Q928", "PH"), // Philippines
            Map.entry("Q252", "ID"), // Indonesia
            Map.entry("Q833", "MY"), // Malaysia
            Map.entry("Q334", "SG"), // Singapore
            Map.entry("Q801", "IL"), // Israel
            Map.entry("Q794", "IR"), // Iran
            Map.entry("Q796", "IQ"), // Iraq
            Map.entry("Q858", "SY"), // Syria
            Map.entry("Q822", "LB"), // Lebanon
            Map.entry("Q810", "JO"), // Jordan
            Map.entry("Q851", "SA"), // Saudi Arabia
            Map.entry("Q40", "AT"), // Austria
            Map.entry("Q213", "CZ"), // Czech Republic
            Map.entry("Q28", "HU"), // Hungary
            Map.entry("Q218", "RO"), // Romania
            Map.entry("Q212", "UA") // Ukraine
    );

    public static final Set<String> AFRICAN_CODES = Set.copyOf(AFRICA.values());

    /** ISO code for a Wikidata country id, or null when it isn't mapped. */
    public static String iso(String qid) {
        String code = AFRICA.get(qid);
        return code != null ? code : OTHERS.get(qid);
    }
}
