import type { Song } from './types';

export const genres = ['New Songs', 'Bollywood', 'Punjabi', 'Indi-Pop', 'Classical', 'Sufi', 'Ghazal'];

// This data is now for fallback purposes or if youtube api is not available.
export const songs: Song[] = [
  { id: 'Yc55-4V2j7A', videoId: 'Yc55-4V2j7A', title: 'Jai Ho', artist: 'A.R. Rahman, Sukhwinder Singh', duration: 319, genre: 'Bollywood', album: 'Slumdog Millionaire', year: 2008, thumbnailUrl: 'https://i.ytimg.com/vi/Yc55-4V2j7A/hqdefault.jpg', search_keywords: ['jai', 'ho', 'a.r.', 'rahman', 'sukhwinder', 'singh', 'bollywood', 'slumdog', 'millionaire'] },
  { id: 'k4yXQkG2s1E', videoId: 'k4yXQkG2s1E', title: 'Kala Chashma', artist: 'Amar Arshi, Badshah, Neha Kakkar', duration: 187, genre: 'Bollywood', album: 'Baar Baar Dekho', year: 2016, thumbnailUrl: 'https://i.ytimg.com/vi/k4yXQkG2s1E/hqdefault.jpg', search_keywords: ['kala', 'chashma', 'amar', 'arshi', 'badshah', 'neha', 'kakkar', 'bollywood', 'baar', 'baar', 'dekho'] },
  { id: 'l1gunA91Y-w', videoId: 'l1gunA91Y-w', title: 'Tujhe Dekha To', artist: 'Lata Mangeshkar, Kumar Sanu', duration: 302, genre: 'Bollywood', album: 'Dilwale Dulhania Le Jayenge', year: 1995, thumbnailUrl: 'https://i.ytimg.com/vi/l1gunA91Y-w/hqdefault.jpg', search_keywords: ['tujhe', 'dekha', 'to', 'lata', 'mangeshkar', 'kumar', 'sanu', 'bollywood', 'dilwale', 'dulhania', 'le', 'jayenge'] },
];
