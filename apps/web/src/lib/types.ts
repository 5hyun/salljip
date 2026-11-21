export type SearchForm = { regionCode:string; date:string; equityKrw:number; loanKrw:number; areaMin:number; areaMax:number; acquisitionCostKrw:number };
export type PropertyType = "APT"|"ROW"|"MULTI"|"RH"|"OFFI";
export const PROPERTY_TYPE_LABEL:Record<PropertyType,string> = { APT:"아파트", ROW:"연립", MULTI:"다세대", RH:"빌라", OFFI:"오피스텔" };
export type Candidate = { building:{ id:string; regionCode:string; name:string; address:string; longitude:number; latitude:number; households:number; builtYear:number; propertyType?:PropertyType }; observedTradeCount:number; historicalPriceRange:{min:number;median:number;max:number}; currentComparablePriceRange:{min:number;median:number;max:number}|null; matchedAreaMin:number; matchedAreaMax:number; lastTradeDate:string; confidence:"HIGH"|"LOW"; exclusions:string[]; changeRatePercent:number|null };
export type HistoricalResponse = { results:Candidate[]; assumptions:Record<string,unknown>; dataAsOf:string; sources:Array<Record<string,string>>; warnings:string[]; nextCursor:string|null; calculationVersion:string; distribution:{total:number;increased:number;decreased:number;medianChangePercent:number} };
export type SimulationResponse = { id:string; homeNetWorthKrw:number; rentAndInvestNetWorthKrw:number; differenceKrw:number; remainingLoanKrw:number; totalInterestKrw:number; totalHousingCostKrw:number; monthlyCashFlows:Array<{month:number;homePayment:number;loanInterest:number;loanPrincipal:number;rentPayment:number;investmentBalance:number}>; assumptions:Record<string,unknown>; engineVersion:string; calculatedAt:string; warnings:string[] };
export type AssetResult = { assetKey:string; displayName:string; initialKrw:number; finalKrw:number; returnPercent:number; basis:string; sourceStatus:string };
export type AssetComparison = { assets:AssetResult[]; dataAsOf:string; warnings:string[] };
export type MarketIndicator = { code:string; name:string; value:number; unit:string; period:string; releasedAt:string; source:string; official:boolean };
export type Explanation = { headline:string; summary:string; evidence:Array<{label:string;value:string;interpretation:string}>; caveats:string[]; explanationVersion:string };

export type TransactionType = "SALE" | "JEONSE" | "MONTHLY";
export type MapMode = "CURRENT" | "PAST";
export type MapStyleId = "NORMAL" | "TERRAIN" | "HYBRID";
export type Building = Candidate["building"];
export type Trade = {
  id:string;
  buildingId:string;
  contractDate:string;
  areaM2:number;
  priceKrw:number;
  monthlyRentKrw:number;
  floor:number;
  transactionType:TransactionType;
  canceled:boolean;
  sourceId:string;
};
export type MapListing = {
  building:Building;
  latestTrade:Trade|null;
  previousTrade:Trade|null;
  observedTradeCount:number;
  dataAsOf:string;
  sourceStatus:string;
};
export type MapFilters = {
  regionCode:string;
  transactionType:TransactionType;
  priceMin:number;
  priceMax:number;
  areaMin:number;
  areaMax:number;
};
export type MapMarkerItem = {
  id:string;
  name:string;
  longitude:number;
  latitude:number;
  primaryPrice:number|null;
  monthlyRentKrw:number;
  changeRatePercent:number|null;
  eligible:boolean;
  confidence:"HIGH"|"LOW";
};
export type MapViewport = {
  bbox:string;
  zoom:number;
  center:[number,number];
};

export type UsageProvider = {
  id:string;
  label:string;
  consoleUrl:string;
  dailyLimit:number;
};

export type DailyUsage = {
  source:string;
  provider:string;
  date:string;
  calls:number;
  errors:number;
};

export type SyncAttempt = {
  attemptedAt:string;
  manual:boolean;
  success:boolean;
  message:string|null;
};

export type UsageReport = {
  source:string;
  providers:UsageProvider[];
  sync:{
    sender:boolean;
    target:string|null;
    schedule:string|null;
    lastAttempt:SyncAttempt|null;
    received:{source:string;receivedAt:string;rowCount:number}[];
  };
  days:DailyUsage[];
};
