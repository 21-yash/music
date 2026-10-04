"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.YouTubeProvider = void 0;
var ytmusic_api_1 = __importDefault(require("ytmusic-api"));
var YouTubeProvider = /** @class */ (function () {
    function YouTubeProvider() {
        this.id = 'youtube';
        this.name = 'YouTube';
        this.isInitialized = false;
        this.ytmusic = new ytmusic_api_1.default();
    }
    YouTubeProvider.prototype.ensureInitialized = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        if (!!this.isInitialized) return [3 /*break*/, 2];
                        return [4 /*yield*/, this.ytmusic.initialize()];
                    case 1:
                        _a.sent();
                        this.isInitialized = true;
                        _a.label = 2;
                    case 2: return [2 /*return*/];
                }
            });
        });
    };
    YouTubeProvider.prototype.search = function (query_1) {
        return __awaiter(this, arguments, void 0, function (query, _page, limit) {
            var results, songs;
            var _this = this;
            if (_page === void 0) { _page = 1; }
            if (limit === void 0) { limit = 20; }
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.ensureInitialized()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.ytmusic.searchSongs(query)];
                    case 2:
                        results = _a.sent();
                        songs = results.slice(0, limit).map(function (s) {
                            var _a, _b;
                            return ({
                                id: s.videoId,
                                title: s.name,
                                artists: s.artists.map(function (a) { return ({
                                    id: a.artistId || '',
                                    name: a.name,
                                    imageUrl: null
                                }); }),
                                album: s.album ? {
                                    id: s.album.albumId,
                                    title: s.album.name,
                                    imageUrl: null
                                } : null,
                                duration: s.duration,
                                imageUrl: ((_b = (_a = s.thumbnails) === null || _a === void 0 ? void 0 : _a[s.thumbnails.length - 1]) === null || _b === void 0 ? void 0 : _b.url) || null,
                                year: '',
                                language: '',
                                hasLyrics: false,
                                playCount: 0,
                                label: '',
                                streamRef: s.videoId, // use videoId as the stream reference
                                providerId: _this.id
                            });
                        });
                        return [2 /*return*/, {
                                songs: songs,
                                albums: [],
                                artists: [],
                                totalSongs: songs.length, // YTMusic API doesn't provide total results easily for generic search
                                query: query
                            }];
                }
            });
        });
    };
    YouTubeProvider.prototype.getTrending = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                return [2 /*return*/, []]; // Optional: Could implement using YTMusic charts
            });
        });
    };
    YouTubeProvider.prototype.getSong = function (id) {
        return __awaiter(this, void 0, void 0, function () {
            var song;
            var _a, _b;
            return __generator(this, function (_c) {
                switch (_c.label) {
                    case 0: return [4 /*yield*/, this.ensureInitialized()];
                    case 1:
                        _c.sent();
                        return [4 /*yield*/, this.ytmusic.getSong(id)];
                    case 2:
                        song = _c.sent();
                        if (!song)
                            return [2 /*return*/, null];
                        return [2 /*return*/, {
                                id: song.videoId,
                                title: song.name,
                                artists: song.artist ? [{
                                        id: song.artist.artistId || '',
                                        name: song.artist.name,
                                        imageUrl: null
                                    }] : [],
                                album: null,
                                duration: 0, // getSong doesn't always return duration in ytmusic-api
                                imageUrl: ((_b = (_a = song.thumbnails) === null || _a === void 0 ? void 0 : _a[song.thumbnails.length - 1]) === null || _b === void 0 ? void 0 : _b.url) || null,
                                year: '',
                                language: '',
                                hasLyrics: false,
                                playCount: 0,
                                label: '',
                                streamRef: song.videoId,
                                providerId: this.id
                            }];
                }
            });
        });
    };
    YouTubeProvider.prototype.getAlbum = function (_id) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                return [2 /*return*/, null];
            });
        });
    };
    YouTubeProvider.prototype.getArtist = function (_id) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                return [2 /*return*/, null];
            });
        });
    };
    YouTubeProvider.prototype.getPlaylist = function (_id) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                return [2 /*return*/, null];
            });
        });
    };
    YouTubeProvider.prototype.getSongsByIds = function (_ids) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                return [2 /*return*/, []];
            });
        });
    };
    YouTubeProvider.prototype.resolveStreamUrl = function (streamRef, _quality) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                // For YouTube, we just return the videoId. The Android client will use NewPipeExtractor to resolve the stream.
                return [2 /*return*/, {
                        type: 'youtube',
                        videoId: streamRef
                    }];
            });
        });
    };
    return YouTubeProvider;
}());
exports.YouTubeProvider = YouTubeProvider;
