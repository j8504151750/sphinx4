/*     */ package edu.cmu.sphinx.decoder.search;
/*     */ 
/*     */ import edu.cmu.sphinx.decoder.pruner.Pruner;
/*     */ import edu.cmu.sphinx.decoder.scorer.AcousticScorer;
/*     */ import edu.cmu.sphinx.frontend.Data;
/*     */ import edu.cmu.sphinx.linguist.Linguist;
/*     */ import edu.cmu.sphinx.linguist.SearchState;
/*     */ import edu.cmu.sphinx.linguist.SearchStateArc;
/*     */ import edu.cmu.sphinx.linguist.acoustic.tiedstate.Loader;
/*     */ import edu.cmu.sphinx.linguist.acoustic.tiedstate.Sphinx3Loader;
/*     */ import edu.cmu.sphinx.linguist.allphone.PhoneHmmSearchState;
/*     */ import edu.cmu.sphinx.linguist.lextree.LexTreeLinguist;
/*     */ import edu.cmu.sphinx.result.Result;
/*     */ import edu.cmu.sphinx.util.props.PropertyException;
/*     */ import edu.cmu.sphinx.util.props.PropertySheet;
/*     */ import edu.cmu.sphinx.util.props.S4Component;
/*     */ import edu.cmu.sphinx.util.props.S4Double;
/*     */ import edu.cmu.sphinx.util.props.S4Integer;
/*     */ import java.util.Arrays;
/*     */ import java.util.HashMap;
/*     */ import java.util.LinkedList;
/*     */ import java.util.Map;
/*     */ 
/*     */ public class WordPruningBreadthFirstLookaheadSearchManager extends WordPruningBreadthFirstSearchManager {
/*     */   @S4Component(type = Loader.class)
/*     */   public static final String PROP_LOADER = "loader";
/*     */   
/*     */   @S4Component(type = Linguist.class)
/*     */   public static final String PROP_FASTMATCH_LINGUIST = "fastmatchLinguist";
/*     */   
/*     */   @S4Component(type = ActiveListFactory.class)
/*     */   public static final String PROP_FM_ACTIVE_LIST_FACTORY = "fastmatchActiveListFactory";
/*     */   
/*     */   @S4Double(defaultValue = 1.0D)
/*     */   public static final String PROP_LOOKAHEAD_PENALTY_WEIGHT = "lookaheadPenaltyWeight";
/*     */   
/*     */   @S4Integer(defaultValue = 5)
/*     */   public static final String PROP_LOOKAHEAD_WINDOW = "lookaheadWindow";
/*     */   
/*     */   private Linguist fastmatchLinguist;
/*     */   
/*     */   private Loader loader;
/*     */   
/*     */   private ActiveListFactory fastmatchActiveListFactory;
/*     */   
/*     */   private int lookaheadWindow;
/*     */   
/*     */   private float lookaheadWeight;
/*     */   
/*     */   private HashMap<Integer, Float> penalties;
/*     */   
/*     */   private LinkedList<FrameCiScores> ciScores;
/*     */   
/*     */   private int currentFastMatchFrameNumber;
/*     */   
/*     */   protected ActiveList fastmatchActiveList;
/*     */   
/*     */   protected Map<SearchState, Token> fastMatchBestTokenMap;
/*     */   
/*     */   private boolean fastmatchStreamEnd;
/*     */   
/*     */   public WordPruningBreadthFirstLookaheadSearchManager(Linguist paramLinguist1, Linguist paramLinguist2, Loader paramLoader, Pruner paramPruner, AcousticScorer paramAcousticScorer, ActiveListManager paramActiveListManager, ActiveListFactory paramActiveListFactory, boolean paramBoolean1, double paramDouble, int paramInt1, boolean paramBoolean2, boolean paramBoolean3, int paramInt2, float paramFloat1, int paramInt3, float paramFloat2, boolean paramBoolean4) {
/*  63 */     super(paramLinguist1, paramPruner, paramAcousticScorer, paramActiveListManager, paramBoolean1, paramDouble, paramInt1, paramBoolean2, paramBoolean3, paramInt3, paramFloat2, paramBoolean4);
/*  64 */     this.loader = paramLoader;
/*  65 */     this.fastmatchLinguist = paramLinguist2;
/*  66 */     this.fastmatchActiveListFactory = paramActiveListFactory;
/*  67 */     this.lookaheadWindow = paramInt2;
/*  68 */     this.lookaheadWeight = paramFloat1;
/*  69 */     if (paramInt2 < 1 || paramInt2 > 10)
/*  70 */       throw new IllegalArgumentException("Unsupported lookahead window size: " + paramInt2 + ". Value in range [1..10] is expected"); 
/*  71 */     this.ciScores = new LinkedList<>();
/*  72 */     this.penalties = new HashMap<>();
/*  73 */     if (paramLoader instanceof Sphinx3Loader && ((Sphinx3Loader)paramLoader).hasTiedMixtures())
/*  74 */       ((Sphinx3Loader)paramLoader).setGauScoresQueueLength(paramInt2 + 2); 
/*     */   }
/*     */   
/*     */   public WordPruningBreadthFirstLookaheadSearchManager() {}
/*     */   
/*     */   public void newProperties(PropertySheet paramPropertySheet) throws PropertyException {
/*  80 */     super.newProperties(paramPropertySheet);
/*  81 */     this.fastmatchLinguist = (Linguist)paramPropertySheet.getComponent("fastmatchLinguist");
/*  82 */     this.fastmatchActiveListFactory = (ActiveListFactory)paramPropertySheet.getComponent("fastmatchActiveListFactory");
/*  83 */     this.loader = (Loader)paramPropertySheet.getComponent("loader");
/*  84 */     this.lookaheadWindow = paramPropertySheet.getInt("lookaheadWindow");
/*  85 */     this.lookaheadWeight = paramPropertySheet.getFloat("lookaheadPenaltyWeight");
/*  86 */     if (this.lookaheadWindow < 1 || this.lookaheadWindow > 10)
/*  87 */       throw new PropertyException(WordPruningBreadthFirstLookaheadSearchManager.class.getName(), "lookaheadWindow", "Unsupported lookahead window size: " + this.lookaheadWindow + ". Value in range [1..10] is expected"); 
/*  88 */     this.ciScores = new LinkedList<>();
/*  89 */     this.penalties = new HashMap<>();
/*  90 */     if (this.loader instanceof Sphinx3Loader && ((Sphinx3Loader)this.loader).hasTiedMixtures())
/*  91 */       ((Sphinx3Loader)this.loader).setGauScoresQueueLength(this.lookaheadWindow + 2); 
/*     */   }
/*     */   
/*     */   public Result recognize(int paramInt) {
/*  95 */     boolean bool = false;
/*  96 */     Result result = null;
/*  97 */     this.streamEnd = false;
/*  98 */     for (byte b = 0; b < paramInt && !bool; b++) {
/*  99 */       if (!this.fastmatchStreamEnd)
/* 100 */         fastMatchRecognize(); 
/* 101 */       this.penalties.clear();
/* 102 */       this.ciScores.poll();
/* 103 */       bool = recognize();
/*     */     } 
/* 105 */     if (!this.streamEnd)
/* 106 */       result = new Result(this.loserManager, this.activeList, this.resultList, this.currentCollectTime, bool, this.linguist.getSearchGraph().getWordTokenFirst(), true); 
/* 107 */     if (this.showTokenCount)
/* 108 */       showTokenCount(); 
/* 109 */     return result;
/*     */   }
/*     */   
/*     */   private void fastMatchRecognize() {
/* 113 */     boolean bool = scoreFastMatchTokens();
/* 114 */     if (bool) {
/* 115 */       pruneFastMatchBranches();
/* 116 */       this.currentFastMatchFrameNumber++;
/* 117 */       createFastMatchBestTokenMap();
/* 118 */       growFastmatchBranches();
/*     */     } 
/*     */   }
/*     */   
/*     */   protected void createFastMatchBestTokenMap() {
/* 123 */     int i = this.fastmatchActiveList.size() * 10;
/* 124 */     if (i == 0)
/* 125 */       i = 1; 
/* 126 */     this.fastMatchBestTokenMap = new HashMap<>(i);
/*     */   }
/*     */   
/*     */   protected void localStart() {
/* 130 */     this.currentFastMatchFrameNumber = 0;
/* 131 */     if (this.loader instanceof Sphinx3Loader && ((Sphinx3Loader)this.loader).hasTiedMixtures())
/* 132 */       ((Sphinx3Loader)this.loader).clearGauScores(); 
/* 133 */     this.fastmatchActiveList = this.fastmatchActiveListFactory.newInstance();
/* 134 */     SearchState searchState = this.fastmatchLinguist.getSearchGraph().getInitialState();
/* 135 */     this.fastmatchActiveList.add(new Token(searchState, this.currentFastMatchFrameNumber));
/* 136 */     createFastMatchBestTokenMap();
/* 137 */     growFastmatchBranches();
/* 138 */     this.fastmatchStreamEnd = false;
/* 139 */     for (byte b = 0; b < this.lookaheadWindow - 1 && !this.fastmatchStreamEnd; b++)
/* 140 */       fastMatchRecognize(); 
/* 141 */     super.localStart();
/*     */   }
/*     */   
/*     */   protected void growFastmatchBranches() {
/* 145 */     this.growTimer.start();
/* 146 */     ActiveList activeList = this.fastmatchActiveList;
/* 147 */     this.fastmatchActiveList = this.fastmatchActiveListFactory.newInstance();
/* 148 */     float f1 = activeList.getBeamThreshold();
/* 149 */     float[] arrayOfFloat = new float[5000];
/* 150 */     Arrays.fill(arrayOfFloat, -3.4028235E38F);
/* 151 */     float f2 = -3.4028235E38F;
/* 152 */     for (Token token : activeList) {
/* 153 */       float f = token.getScore();
/* 154 */       if (f < f1)
/*     */         continue; 
/* 156 */       if (token.getSearchState() instanceof PhoneHmmSearchState) {
/* 157 */         int i = ((PhoneHmmSearchState)token.getSearchState()).getBaseId();
/* 158 */         if (arrayOfFloat[i] < f)
/* 159 */           arrayOfFloat[i] = f; 
/* 160 */         if (f2 < f)
/* 161 */           f2 = f; 
/*     */       } 
/* 163 */       collectFastMatchSuccessorTokens(token);
/*     */     } 
/* 165 */     this.ciScores.add(new FrameCiScores(arrayOfFloat, f2));
/* 166 */     this.growTimer.stop();
/*     */   }
/*     */   
/*     */   protected boolean scoreFastMatchTokens() {
/* 170 */     this.scoreTimer.start();
/* 171 */     Data data = this.scorer.calculateScoresAndStoreData(this.fastmatchActiveList.getTokens());
/* 172 */     this.scoreTimer.stop();
/* 173 */     Token token = null;
/* 174 */     if (data instanceof Token) {
/* 175 */       token = (Token)data;
/*     */     } else {
/* 177 */       this.fastmatchStreamEnd = true;
/*     */     } 
/* 179 */     boolean bool = (token != null) ? true : false;
/* 180 */     this.fastmatchActiveList.setBestToken(token);
/* 181 */     monitorStates(this.fastmatchActiveList);
/* 182 */     this.curTokensScored.value += this.fastmatchActiveList.size();
/* 183 */     this.totalTokensScored.value += this.fastmatchActiveList.size();
/* 184 */     return bool;
/*     */   }
/*     */   
/*     */   protected void pruneFastMatchBranches() {
/* 188 */     this.pruneTimer.start();
/* 189 */     this.fastmatchActiveList = this.pruner.prune(this.fastmatchActiveList);
/* 190 */     this.pruneTimer.stop();
/*     */   }
/*     */   
/*     */   protected Token getFastMatchBestToken(SearchState paramSearchState) {
/* 194 */     return this.fastMatchBestTokenMap.get(paramSearchState);
/*     */   }
/*     */   
/*     */   protected void setFastMatchBestToken(Token paramToken, SearchState paramSearchState) {
/* 198 */     this.fastMatchBestTokenMap.put(paramSearchState, paramToken);
/*     */   }
/*     */   
/*     */   protected void collectFastMatchSuccessorTokens(Token paramToken) {
/* 202 */     SearchState searchState = paramToken.getSearchState();
/* 203 */     SearchStateArc[] arrayOfSearchStateArc = searchState.getSuccessors();
/* 204 */     for (SearchStateArc searchStateArc : arrayOfSearchStateArc) {
/* 205 */       SearchState searchState1 = searchStateArc.getState();
/* 206 */       float f = paramToken.getScore() + searchStateArc.getProbability();
/* 207 */       Token token = getResultListPredecessor(paramToken);
/* 208 */       if (!searchState1.isEmitting()) {
/* 209 */         Token token1 = new Token(token, searchState1, f, searchStateArc.getInsertionProbability(), searchStateArc.getLanguageProbability(), this.currentFastMatchFrameNumber);
/* 210 */         this.tokensCreated.value++;
/* 211 */         if (!isVisited(token1))
/* 212 */           collectFastMatchSuccessorTokens(token1); 
/*     */       } else {
/* 214 */         Token token1 = getFastMatchBestToken(searchState1);
/* 215 */         if (token1 == null) {
/* 216 */           Token token2 = new Token(token, searchState1, f, searchStateArc.getInsertionProbability(), searchStateArc.getLanguageProbability(), this.currentFastMatchFrameNumber);
/* 217 */           this.tokensCreated.value++;
/* 218 */           setFastMatchBestToken(token2, searchState1);
/* 219 */           this.fastmatchActiveList.add(token2);
/* 220 */         } else if (token1.getScore() <= f) {
/* 221 */           token1.update(token, searchState1, f, searchStateArc.getInsertionProbability(), searchStateArc.getLanguageProbability(), this.currentFastMatchFrameNumber);
/*     */         } 
/*     */       } 
/*     */     } 
/*     */   }
/*     */   
/*     */   protected void collectSuccessorTokens(Token paramToken) {
/* 228 */     if (paramToken.isFinal()) {
/* 229 */       this.resultList.add(getResultListPredecessor(paramToken));
/*     */       return;
/*     */     } 
/* 232 */     if (!paramToken.isEmitting() && this.keepAllTokens && isVisited(paramToken))
/*     */       return; 
/* 234 */     SearchState searchState = paramToken.getSearchState();
/* 235 */     SearchStateArc[] arrayOfSearchStateArc = searchState.getSuccessors();
/* 236 */     Token token = getResultListPredecessor(paramToken);
/* 237 */     float f1 = paramToken.getScore();
/* 238 */     float f2 = this.activeList.getBeamThreshold();
/* 239 */     boolean bool = (searchState instanceof LexTreeLinguist.LexTreeNonEmittingHMMState || searchState instanceof LexTreeLinguist.LexTreeWordState || searchState instanceof LexTreeLinguist.LexTreeEndUnitState) ? true : false;
/* 240 */     for (SearchStateArc searchStateArc : arrayOfSearchStateArc) {
/* 241 */       SearchState searchState1 = searchStateArc.getState();
/* 242 */       if (bool && searchState1 instanceof LexTreeLinguist.LexTreeHMMState) {
/* 244 */         int i = ((LexTreeLinguist.LexTreeHMMState)searchState1).getHMMState().getHMM().getBaseUnit().getBaseID();
/*     */         Float float_;
/* 246 */         if ((float_ = this.penalties.get(Integer.valueOf(i))) == null)
/* 247 */           float_ = updateLookaheadPenalty(i); 
/* 248 */         if (f1 + this.lookaheadWeight * float_.floatValue() < f2)
/*     */           continue; 
/*     */       } 
/* 251 */       if (this.checkStateOrder)
/* 252 */         checkStateOrder(searchState, searchState1); 
/* 253 */       float f = f1 + searchStateArc.getProbability();
/* 254 */       Token token1 = getBestToken(searchState1);
/* 255 */       if (token1 == null) {
/* 256 */         Token token2 = new Token(token, searchState1, f, searchStateArc.getInsertionProbability(), searchStateArc.getLanguageProbability(), this.currentCollectTime);
/* 257 */         this.tokensCreated.value++;
/* 258 */         setBestToken(token2, searchState1);
/* 259 */         activeListAdd(token2);
/* 260 */       } else if (token1.getScore() < f) {
/* 261 */         Token token2 = token1.getPredecessor();
/* 262 */         token1.update(token, searchState1, f, searchStateArc.getInsertionProbability(), searchStateArc.getLanguageProbability(), this.currentCollectTime);
/* 263 */         if (this.buildWordLattice && searchState1 instanceof edu.cmu.sphinx.linguist.WordSearchState)
/* 264 */           this.loserManager.addAlternatePredecessor(token1, token2); 
/* 265 */       } else if (this.buildWordLattice && searchState1 instanceof edu.cmu.sphinx.linguist.WordSearchState && token != null) {
/* 267 */         this.loserManager.addAlternatePredecessor(token1, token);
/*     */       } 
/*     */       continue;
/*     */     } 
/*     */   }
/*     */   
/*     */   private Float updateLookaheadPenalty(int paramInt) {
/* 274 */     if (this.ciScores.isEmpty())
/* 275 */       return Float.valueOf(0.0F); 
/* 276 */     float f = -3.4028235E38F;
/* 277 */     for (FrameCiScores frameCiScores : this.ciScores) {
/* 278 */       float f1 = frameCiScores.scores[paramInt] - frameCiScores.maxScore;
/* 279 */       if (f1 > f)
/* 280 */         f = f1; 
/*     */     } 
/* 282 */     this.penalties.put(Integer.valueOf(paramInt), Float.valueOf(f));
/* 283 */     return Float.valueOf(f);
/*     */   }
/*     */   
/*     */   private class FrameCiScores {
/*     */     public final float[] scores;
/*     */     
/*     */     public final float maxScore;
/*     */     
/*     */     public FrameCiScores(float[] param1ArrayOffloat, float param1Float) {
/* 292 */       this.scores = param1ArrayOffloat;
/* 293 */       this.maxScore = param1Float;
/*     */     }
/*     */   }
/*     */ }


/* Location:              C:\Users\j8504\OneDrive\桌面\sphinx4-core-5prealpha-SNAPSHOT.jar!\edu\cmu\sphinx\decoder\search\WordPruningBreadthFirstLookaheadSearchManager.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */