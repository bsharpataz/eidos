package org.clulab.classification

object ClassificationHelper {

  val stopWords = Set("be", "do", "have", "want", "need", "believe", "know", "see", "say", "report")


  val fpPeace = Seq("plans wage war", "troops", "aggression", "deployment", "cease-fire", "cessation of hostilities", "anti-war", "sending troops", "support foreign wars")
  val fpIntlCrisis = Seq("bombing embassy", "embassy", "aggression interference", "abduction of countrymen")
  val fpTerritorial = Seq("grab island territory", "territorial integrity", "territory", "landing island")
  val fpAntiForeign = Seq("withdraw foreign troops forces", "live-firing drills", "presence of air base")
  val fpDiplomaticReln = Seq("normalize ties with regime", "not receive envoy", "diplomatic relations")
  val fpRecognize = Seq("recognize", "recognition")
  val fpTrade = Seq("international trade", "trade", "free trade talks", "trade treaty", "import", "imports", "export", "exports")
  val fpAid = Seq("food rations", "emergency food support", "food aid", "food water distribution", "water trucking", "humanitarian assistance",
    "request aid")
  val FP = Seq(
    fpPeace,
    fpIntlCrisis,
    fpTerritorial,
    fpAntiForeign,
    fpDiplomaticReln,
    fpRecognize,
    fpTrade,
    fpAid
  )

  val domLeadershipChange = Seq("anti-government", "leadership change", "quit post position", "resign", "overthrow regime")
  val domElection = Seq("election", "referendum", "electoral commissions", "new elections held", "vote",
    "election results", "election outcome", "re-election")
  val domMediaFreedom = Seq("media freedom", "free press", "curb on electronic media",
    "murder imprisonment journalist")
  val domPhysicalInteg = Seq("killing of colleagues", "repressed protesters", "imprisonment opposition politicians", "arrested protestors",
    "bloodshed", "anti-war anti-warlord anti-gunlord")
  val domSubNatlConflict = Seq("subnational conflict", "internal dispute", "expel ethnic", "religious fighting", "civil war", "separatist",
    "kidnapping ethnic people")
  val domWageWelfare = Seq("low wages", "poor living working conditions", "low income", "unpaid wages", "long working hours",
    "bad unsafe work conditions", "welfare poor", "price inflation", "soaring prices", "high inflation")
  val domIndustryMonetary = Seq("industry within national structure", "privatize industry", "privatization industry", "bank",
    "bank accounts", "monetary policy", "interest rates", "government guarantees loans")
  val domEducation = Seq("education", "elementary education rights", "school", "language education", "studies",
    "courses", "university and colleges")
  val DOM = Seq(
    domLeadershipChange,
    domElection,
    domMediaFreedom,
    domPhysicalInteg,
    domSubNatlConflict,
    domWageWelfare,
    domIndustryMonetary,
    //domEducation
  )

}
