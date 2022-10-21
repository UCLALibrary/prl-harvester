--
-- PostgreSQL globals initialization
--

SET default_transaction_read_only = off;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

--
-- Roles
--

CREATE ROLE prl;
ALTER ROLE prl WITH NOSUPERUSER INHERIT NOCREATEROLE NOCREATEDB LOGIN NOREPLICATION NOBYPASSRLS PASSWORD '${test.db.password}';

--
-- PostgreSQL database initialization
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;
SET default_tablespace = '';
SET default_table_access_method = heap;

--
-- Name: items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.institutions (
    id SERIAL PRIMARY KEY,
    name text NOT NULL,
    description text NOT NULL,
    location text NOT NULL,
    email text,
    phone text,
    webContact text,
    website text NOT NULL
);

CREATE TABLE public.harvestjobs (
    id SERIAL PRIMARY KEY,
    institutionID INT,
    repositoryBaseURL TEXT NOT NULL,
    metadataPrefix TEXT NOT NULL,
    sets TEXT [],
    lastSuccessfulRun TIMESTAMPTZ,
    scheduleCronExpression TEXT NOT NULL
);

ALTER TABLE public.institutions OWNER TO postgres;

ALTER TABLE public.harvestjobs OWNER TO postgres;

--
-- Name: COLUMN institutions.id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.institutions.id IS 'The unique identifier of an institution';

--
-- Name: COLUMN institutions.name; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.institutions.name IS 'The name of an institution';

--
-- Name: COLUMN institutions.description; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.institutions.description IS 'The description of an institution';

--
-- Name: COLUMN institutions.location; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.institutions.location IS 'The location of an institution';

--
-- Name: COLUMN institutions.email; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.institutions.email IS 'The email contact for an institution';

--
-- Name: COLUMN institutions.phone; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.institutions.phone IS 'The phone contact for an institution';

--
-- Name: COLUMN institutions.webContact; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.institutions.webContact IS 'The Web contact for an institution';

--
-- Name: COLUMN institutions.website; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.institutions.website IS 'The website for an institution';

--
-- Name: COLUMN harvestjobs.id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.harvestjobs.id IS 'The unique identifier for a harvest job';

--
-- Name: COLUMN harvestjobs.institutionID; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.harvestjobs.institutionID IS 'The unique identifier for a related institution';

--
-- Name: COLUMN harvestjobs.repositoryBaseURL; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.harvestjobs.repositoryBaseURL IS 'The base URL for a harvested repository';

--
-- Name: COLUMN harvestjobs.metadataPrefix; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.harvestjobs.metadataPrefix IS 'The metadata prefix for a harvest job';

--
-- Name: COLUMN harvestjobs.sets; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.harvestjobs.sets IS 'The array of harvest sets';

--
-- Name: COLUMN harvestjobs.lastSuccessfulRun; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.harvestjobs.lastSuccessfulRun IS 'The timestamp for the last successful harvest';

--
-- Name: COLUMN harvestjobs.scheduleCronExpression; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.harvestjobs.scheduleCronExpression IS 'The cron expression for a harvest job';

--
-- Name: items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.institutions (id, name, description, location, email, phone, webContact, website) FROM stdin;
\.

--
-- Name: origins; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.harvestjobs (id, institutionID, repositoryBaseURL, metadataPrefix, sets, lastSuccessfulRun,
  scheduleCronExpression) FROM stdin;
\.

--
-- Name: harvestjobs_fkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.harvestjobs
    ADD CONSTRAINT harvestjobs_fkey FOREIGN KEY(institutionID) REFERENCES public.institutions(id);

--
-- Name: TABLE institutions; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.institutions TO prl;

--
-- Name: TABLE harvestjobs; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.harvestjobs TO prl;

--

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO prl;

--
-- sample entires for unit/integration test
--

INSERT INTO public.institutions(name, description, location, email, phone, webContact, website) VALUES('Sample 1', 'A sample institution', 'Here', 'this@that.com', '+1 888 123 4567', 'http://acme.edu/1/contact', 'http://acme.edu/1');
INSERT INTO public.institutions(name, description, location, email, phone, webContact, website) VALUES('Sample 2', 'Another sample', 'There', 'that@theother.com', '+1 888 890 1234', 'http://acme.edu/1/contact', 'http://acme.edu/1');
INSERT INTO public.institutions(name, description, location, email, phone, webContact, website) VALUES('Sample 3', 'A third sample', 'Everywhere', 'no@where.com', '+1 888 567 8901', 'http://acme.edu/1/contact', 'http://acme.edu/1');

