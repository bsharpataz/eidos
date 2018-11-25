var bratLocation = 'assets/brat';

// Color names used
var baseConceptColor = '#CCD1D1';
var increaseConceptColor = baseConceptColor; // '#BBDC90';
var decreaseConceptColor = baseConceptColor; // '#FC5C38';
var quantifierColor = '#AED6F1';
var quantifiedConceptColor = baseConceptColor; // '#85C1E9';
var causalEventColor = '#BB8FCE';
var correlationEventColor = '#F7DC6F';
var protestEventColor = '#77bfff';
var demandEventColor = '#fc993c';
var locColor = '#8bd884';
var timeExpressionColor = '#FFA500'
var geoLocationColor = '#FFA500'


head.js(
    // External libraries
    bratLocation + '/client/lib/jquery.min.js',
    bratLocation + '/client/lib/jquery.svg.min.js',
    bratLocation + '/client/lib/jquery.svgdom.min.js',

    // brat helper modules
    bratLocation + '/client/src/configuration.js',
    bratLocation + '/client/src/util.js',
    bratLocation + '/client/src/annotation_log.js',
    bratLocation + '/client/lib/webfont.js',

    // brat modules
    bratLocation + '/client/src/dispatcher.js',
    bratLocation + '/client/src/url_monitor.js',
    bratLocation + '/client/src/visualizer.js'
);

var webFontURLs = [
    bratLocation + '/static/fonts/Astloch-Bold.ttf',
    bratLocation + '/static/fonts/PT_Sans-Caption-Web-Regular.ttf',
    bratLocation + '/static/fonts/Liberation_Sans-Regular.ttf'
];

var collData = {
    entity_types: [ {
        "type"   : "Quantifier",
        "labels" : ["Quantifier", "Quant"],
        // Blue is a nice colour for a person?
        "bgColor": quantifierColor,
        // Use a slightly darker version of the bgColor for the border
        "borderColor": "darken"
    },
    {
            "type"   : "Concept",
            "labels" : ["Concept", "Conc"],
            // Blue is a nice colour for a person?
            //"bgColor": "thistle",
            "bgColor": baseConceptColor,
            // Use a slightly darker version of the bgColor for the border
            "borderColor": "darken"
        },
        {
            "type"   : "Concept-Inc",
            "labels" : ["Concept", "Conc"],
            // Blue is a nice colour for a person?
            //"bgColor": "thistle",
            "bgColor": increaseConceptColor,
            // Use a slightly darker version of the bgColor for the border
            "borderColor": "darken"
        },
        {
            "type"   : "Concept-Dec",
            "labels" : ["Concept", "Conc"],
            // Blue is a nice colour for a person?
            //"bgColor": "thistle",
            "bgColor": decreaseConceptColor,
            // Use a slightly darker version of the bgColor for the border
            "borderColor": "darken"
        },
        {
            "type"   : "Concept-Quant",
            "labels" : ["Concept", "Conc"],
            // Blue is a nice colour for a person?
            //"bgColor": "thistle",
            "bgColor": quantifiedConceptColor,
            // Use a slightly darker version of the bgColor for the border
            "borderColor": "darken"
        },

	// --------------------------- Context -------------------------------------
	{
            "type"   : "TimeExpression",
            "labels" : ["TimeExpression", "TIMEX"],
            // Blue is a nice colour for a person?
            //"bgColor": "thistle",
            "bgColor": timeExpressionColor,
            // Use a slightly darker version of the bgColor for the border
            "borderColor": "darken"
        },
    {
          "type": "Organization",
          "labels":  ["Organization", "ORG"],
          "bgColor": "yellow",
          "borderColor": "darken"
         },
    {
       "type": "Location",
       "labels":  ["Location", "LOC"],
       "bgColor": locColor,
       "borderColor": "darken"
      },
        {
            "type"   : "GeoidPhrases",
            "labels" : ["GeoidPhrases", "GEOLOC"],
            // Blue is a nice colour for a person?
            //"bgColor": "thistle",
            "bgColor": geoLocationColor,
            // Use a slightly darker version of the bgColor for the border
            "borderColor": "darken"
         },
    ],

    event_types: [
      {
        "type": "Increase",
        "labels": ["INC"],
        "bgColor": "lightgreen",
        "borderColor": "darken",
        "arcs": [
            {"type": "theme", "labels": ["theme"], "borderColor": "darken", "bgColor":"violet"},
            {"type": "quantifier", "labels": ["quant"], "borderColor": "darken", "bgColor":"violet"}
        ]
      },

      {
        "type": "Decrease",
        "labels": ["DEC"],
        "bgColor": "red",
        "borderColor": "darken",
        "arcs": [
            {"type": "theme", "labels": ["theme"], "borderColor": "darken", "bgColor":"violet"},
            {"type": "quantifier", "labels": ["quant"], "borderColor": "darken", "bgColor":"violet"}
        ]
      },

      {
        "type": "Causal",
        "labels": ["CAUSAL"],
        "bgColor": causalEventColor,
        "borderColor": "darken",
        "arcs": [
          {"type": "cause", "labels": ["cause"], "borderColor": "darken", "bgColor":"pink"},
          {"type": "effect", "labels": ["effect"], "borderColor": "darken", "bgColor":"pink"}
         ]
      },

      {
        "type": "Correlation",
        "labels": ["CORRELATION"],
        "bgColor": correlationEventColor,
        "borderColor": "darken",
        "arcs": [
          {"type": "cause", "labels": ["cause"], "borderColor": "darken", "bgColor":"pink"},
          {"type": "effect", "labels": ["effect"], "borderColor": "darken", "bgColor":"pink"}
         ]
      },
      {
          "type": "Protest",
          "labels": ["PROTEST"],
          "bgColor": protestEventColor,
          "borderColor": "darken",
          "arcs": [
            {"type": "theme", "labels": ["theme"], "borderColor": "darken", "bgColor":"pink"},
            {"type": "actor", "labels": ["actor"], "borderColor": "darken", "bgColor":"pink"}
           ]
        },
      {
          "type": "Demand",
          "labels": ["DEMAND"],
          "bgColor": demandEventColor,
          "borderColor": "darken",
         "arcs": [
             {"type": "theme", "labels": ["theme"], "borderColor": "darken", "bgColor":"pink"},
             {"type": "actor", "labels": ["actor"], "borderColor": "darken", "bgColor":"pink"}
            ]
        },

    ]
};

// docData is initially empty.
var docData = {};

head.ready(function() {

    var syntaxLiveDispatcher = Util.embed('syntax',
        $.extend({'collection': null}, collData),
        $.extend({}, docData),
        webFontURLs
    );
    var eidosMentionsLiveDispatcher = Util.embed('eidosMentions',
        $.extend({'collection': null}, collData),
        $.extend({}, docData),
        webFontURLs
    );

    $('form').submit(function (event) {

        // stop the form from submitting the normal way and refreshing the page
        event.preventDefault();

        // collect form data
        var formData = {
            'text': $('textarea[name=text]').val(),
            'cagRelevantOnly': $('input[name=cagRelevantOnly]').is(':checked')
        }

        if (!formData.text.trim()) {
            alert("Please write something.");
            return;
        }

        // show spinner
        document.getElementById("overlay").style.display = "block";

        // process the form
        $.ajax({
            type: 'GET',
            url: 'parseText',
            data: formData,
            dataType: 'json',
            encode: true
        })
        .fail(function () {
            // hide spinner
            document.getElementById("overlay").style.display = "none";
            alert("error");
        })
        .done(function (data) {
            console.log(data);
            syntaxLiveDispatcher.post('requestRenderData', [$.extend({}, data.syntax)]);
            eidosMentionsLiveDispatcher.post('requestRenderData', [$.extend({}, data.eidosMentions)]);
            document.getElementById("groundedAdj").innerHTML = data.groundedAdj;
            document.getElementById("parse").innerHTML = data.parse;
            // hide spinner
            document.getElementById("overlay").style.display = "none";
        });

    });
});
